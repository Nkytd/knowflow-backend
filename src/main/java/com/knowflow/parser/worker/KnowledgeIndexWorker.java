package com.knowflow.parser.worker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.integration.llm.EmbeddingModelClient;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.entity.KnowledgeChunkIndexEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkIndexMapper;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.parser.deadletter.support.TaskExecutionFailedException;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.service.KnowledgeIndexTaskExecutionService;
import com.knowflow.parser.support.ParseWorkerIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class KnowledgeIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexWorker.class);
    private static final String STATUS_ENABLED = "ENABLED";

    private final KnowledgeIndexTaskExecutionService knowledgeIndexTaskExecutionService;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;
    private final EmbeddingModelClient embeddingModelClient;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;
    private final ParseWorkerIdentityProvider parseWorkerIdentityProvider;
    private final ParseTaskGovernanceEventService governanceEventService;
    private final ObjectMapper objectMapper;

    public KnowledgeIndexWorker(KnowledgeIndexTaskExecutionService knowledgeIndexTaskExecutionService,
                                KnowledgeChunkMapper knowledgeChunkMapper,
                                KnowledgeChunkIndexMapper knowledgeChunkIndexMapper,
                                EmbeddingModelClient embeddingModelClient,
                                ParseTaskRuntimeTracker parseTaskRuntimeTracker,
                                ParseWorkerIdentityProvider parseWorkerIdentityProvider,
                                ParseTaskGovernanceEventService governanceEventService,
                                ObjectMapper objectMapper) {
        this.knowledgeIndexTaskExecutionService = knowledgeIndexTaskExecutionService;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
        this.embeddingModelClient = embeddingModelClient;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
        this.parseWorkerIdentityProvider = parseWorkerIdentityProvider;
        this.governanceEventService = governanceEventService;
        this.objectMapper = objectMapper;
    }

    public void process(Long taskId, boolean deadLetterOnFailure) {
        String workerId = parseWorkerIdentityProvider.getWorkerId();
        if (!parseTaskRuntimeTracker.tryAcquireWorkerLock(taskId, workerId)) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.DUPLICATE_CONSUMPTION_SKIPPED,
                    "Worker lock already held; duplicated index task message was skipped.",
                    workerId,
                    null);
            log.info("Skip duplicated index task consumption. taskId={}, workerId={}", taskId, workerId);
            return;
        }

        long start = System.currentTimeMillis();
        LocalDateTime attemptStartedAt = null;
        try {
            KnowledgeIndexTaskExecutionService.IndexExecutionContext context =
                    knowledgeIndexTaskExecutionService.startProcessing(taskId);
            if (context == null) {
                governanceEventService.record(taskId,
                        ParseTaskGovernanceEventService.NON_PENDING_MESSAGE_SKIPPED,
                        "Index task is no longer PENDING; message was skipped.",
                        workerId,
                        null);
                return;
            }
            attemptStartedAt = context.task().getStartedAt();

            parseTaskRuntimeTracker.markDequeued(taskId, workerId);
            parseTaskRuntimeTracker.markParsing(taskId, workerId);

            List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .eq(KnowledgeChunkEntity::getDocumentId, context.document().getId())
                            .eq(KnowledgeChunkEntity::getStatus, STATUS_ENABLED)
                            .orderByAsc(KnowledgeChunkEntity::getChunkNo)
            );
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCode.SEARCH_RESULT_INSUFFICIENT, "No parsed chunks were found for indexing");
            }

            List<List<Float>> embeddings = embeddingModelClient.embed(chunks.stream().map(KnowledgeChunkEntity::getContent).toList());
            if (embeddings.size() != chunks.size()) {
                throw new BizException(ErrorCode.MODEL_CALL_FAILED, "Embedding response size does not match chunk count");
            }

            parseTaskRuntimeTracker.markPersisting(taskId, workerId, chunks.size());
            knowledgeChunkIndexMapper.hardDeleteByDocumentId(context.document().getId());

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunkEntity chunk = chunks.get(i);
                List<Float> embedding = embeddings.get(i);
                KnowledgeChunkIndexEntity entity = new KnowledgeChunkIndexEntity();
                entity.setTenantId(chunk.getTenantId());
                entity.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
                entity.setDocumentId(chunk.getDocumentId());
                entity.setChunkId(chunk.getId());
                entity.setEmbeddingProvider(embeddingModelClient.providerName());
                entity.setEmbeddingModel(embeddingModelClient.modelName());
                entity.setEmbeddingDim(embedding.size());
                entity.setVectorNorm(BigDecimal.valueOf(vectorNorm(embedding)).setScale(8, RoundingMode.HALF_UP));
                entity.setEmbeddingJson(toJson(embedding));
                entity.setStatus(STATUS_ENABLED);
                knowledgeChunkIndexMapper.insert(entity);
            }

            long durationMs = System.currentTimeMillis() - start;
            knowledgeIndexTaskExecutionService.completeSuccess(taskId, attemptStartedAt, chunks.size(), durationMs);
            parseTaskRuntimeTracker.markSuccess(taskId, workerId, chunks.size(), durationMs);
            log.info("Index task finished successfully. taskId={}, workerId={}, chunkCount={}", taskId, workerId, chunks.size());
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - start;
            boolean completable = attemptStartedAt != null && knowledgeIndexTaskExecutionService.isCompletable(taskId, attemptStartedAt);
            if (completable) {
                knowledgeIndexTaskExecutionService.completeFailure(taskId, attemptStartedAt, ex.getMessage(), durationMs);
                parseTaskRuntimeTracker.markFailure(taskId, workerId, ex.getMessage());
            } else if (attemptStartedAt != null) {
                governanceEventService.record(taskId,
                        ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                        "Index attempt failure was ignored because the attempt is no longer completable: " + ex.getMessage(),
                        workerId,
                        attemptStartedAt);
            }
            log.error("Index task failed. taskId={}, workerId={}", taskId, workerId, ex);
            if (deadLetterOnFailure && completable) {
                throw new TaskExecutionFailedException(taskId, ex.getMessage());
            }
        } finally {
            parseTaskRuntimeTracker.releaseWorkerLock(taskId, workerId);
        }
    }

    private String toJson(List<Float> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize embedding", ex);
        }
    }

    private double vectorNorm(List<Float> embedding) {
        double sum = 0D;
        for (Float value : embedding) {
            if (value == null) {
                continue;
            }
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
