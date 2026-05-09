package com.knowflow.parser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkIndexMapper;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.knowledge.support.KnowledgeTextSanitizer;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.model.ParsedChunk;
import com.knowflow.parser.service.ParseTaskExecutionService;
import com.knowflow.parser.service.ParseTaskService;
import com.knowflow.parser.support.ParseTaskStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParseTaskExecutionServiceImpl implements ParseTaskExecutionService {

    private static final String CHUNK_STATUS_ENABLED = "ENABLED";

    private final ParseTaskMapper parseTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;
    private final ParseTaskService parseTaskService;
    private final DeadLetterMessageService deadLetterMessageService;
    private final ParseTaskGovernanceEventService governanceEventService;

    public ParseTaskExecutionServiceImpl(ParseTaskMapper parseTaskMapper,
                                         KnowledgeDocumentMapper knowledgeDocumentMapper,
                                         KnowledgeChunkMapper knowledgeChunkMapper,
                                         KnowledgeChunkIndexMapper knowledgeChunkIndexMapper,
                                         ParseTaskService parseTaskService,
                                         DeadLetterMessageService deadLetterMessageService,
                                         ParseTaskGovernanceEventService governanceEventService) {
        this.parseTaskMapper = parseTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
        this.parseTaskService = parseTaskService;
        this.deadLetterMessageService = deadLetterMessageService;
        this.governanceEventService = governanceEventService;
    }

    @Override
    @Transactional
    public ParseExecutionContext startProcessing(Long taskId) {
        ParseTaskEntity existingTask = parseTaskMapper.selectById(taskId);
        if (existingTask == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Parse task not found");
        }
        if (!ParseTaskStateMachine.canStart(existingTask.getStatus())) {
            return null;
        }

        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(existingTask.getDocumentId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document not found for parse task");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = parseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getId, taskId)
                        .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PENDING)
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .set(ParseTaskEntity::getErrorMessage, null)
                        .set(ParseTaskEntity::getStartedAt, now)
                        .set(ParseTaskEntity::getFinishedAt, null)
                        .set(ParseTaskEntity::getDurationMs, null)
        );
        if (updated == 0) {
            return null;
        }

        ParseTaskEntity task = parseTaskMapper.selectById(taskId);
        document.setParseStatus(ParseTaskStateMachine.PROCESSING);
        document.setIndexStatus(ParseTaskStateMachine.PENDING);
        knowledgeDocumentMapper.updateById(document);

        return new ParseExecutionContext(task, document);
    }

    @Override
    @Transactional
    public void completeSuccess(Long taskId, LocalDateTime attemptStartedAt, List<ParsedChunk> chunks, long durationMs) {
        ParseTaskEntity task = getCompletableTask(taskId, attemptStartedAt);
        if (task == null) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Parse success result was ignored because the attempt is no longer completable.",
                    null,
                    attemptStartedAt);
            return;
        }
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document not found for parse task");
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        int completed = parseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getId, taskId)
                        .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .eq(ParseTaskEntity::getStartedAt, attemptStartedAt)
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.SUCCESS)
                        .set(ParseTaskEntity::getFinishedAt, finishedAt)
                        .set(ParseTaskEntity::getDurationMs, durationMs)
                        .set(ParseTaskEntity::getErrorMessage, null)
        );
        if (completed == 0) {
            governanceEventService.record(task,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Parse success result was ignored because task state changed before completion update.",
                    null,
                    attemptStartedAt);
            return;
        }

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, document.getId()));
        knowledgeChunkIndexMapper.hardDeleteByDocumentId(document.getId());

        int insertedChunkCount = 0;
        for (ParsedChunk chunk : chunks) {
            String cleanedContent = KnowledgeTextSanitizer.cleanForChunk(chunk.getContent());
            if (cleanedContent.isBlank()) {
                continue;
            }
            KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
            entity.setTenantId(document.getTenantId());
            entity.setKnowledgeBaseId(document.getKnowledgeBaseId());
            entity.setDocumentId(document.getId());
            entity.setChunkNo(chunk.getChunkNo());
            entity.setContent(cleanedContent);
            entity.setCharCount(cleanedContent.length());
            entity.setTokenCount(Math.max(1, cleanedContent.length() / 4));
            entity.setStatus(CHUNK_STATUS_ENABLED);
            knowledgeChunkMapper.insert(entity);
            insertedChunkCount++;
        }

        document.setChunkCount(insertedChunkCount);
        document.setParseStatus(ParseTaskStateMachine.SUCCESS);
        document.setIndexStatus(ParseTaskStateMachine.PENDING);
        knowledgeDocumentMapper.updateById(document);

        deadLetterMessageService.resolveByTaskId(taskId);
        parseTaskService.createPendingIndexTask(document);
    }

    @Override
    @Transactional
    public void completeFailure(Long taskId, LocalDateTime attemptStartedAt, String errorMessage, long durationMs) {
        ParseTaskEntity task = getCompletableTask(taskId, attemptStartedAt);
        if (task == null) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Parse failure result was ignored because the attempt is no longer completable.",
                    null,
                    attemptStartedAt);
            return;
        }
        int completed = parseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getId, taskId)
                        .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .eq(ParseTaskEntity::getStartedAt, attemptStartedAt)
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.FAILED)
                        .set(ParseTaskEntity::getFinishedAt, LocalDateTime.now())
                        .set(ParseTaskEntity::getDurationMs, durationMs)
                        .set(ParseTaskEntity::getErrorMessage, truncateError(errorMessage))
        );
        if (completed == 0) {
            governanceEventService.record(task,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Parse failure result was ignored because task state changed before completion update.",
                    null,
                    attemptStartedAt);
            return;
        }

        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
        if (document != null) {
            knowledgeChunkMapper.delete(new LambdaUpdateWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getDocumentId, document.getId()));
            knowledgeChunkIndexMapper.hardDeleteByDocumentId(document.getId());
            document.setChunkCount(0);
            document.setParseStatus(ParseTaskStateMachine.FAILED);
            document.setIndexStatus(ParseTaskStateMachine.FAILED);
            knowledgeDocumentMapper.updateById(document);
        }
    }

    @Override
    public boolean isCompletable(Long taskId, LocalDateTime attemptStartedAt) {
        return getCompletableTask(taskId, attemptStartedAt) != null;
    }

    private ParseTaskEntity getCompletableTask(Long taskId, LocalDateTime attemptStartedAt) {
        if (taskId == null || attemptStartedAt == null) {
            return null;
        }
        ParseTaskEntity task = parseTaskMapper.selectById(taskId);
        if (task == null || !ParseTaskStateMachine.canComplete(task.getStatus())) {
            return null;
        }
        return attemptStartedAt.equals(task.getStartedAt()) ? task : null;
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
