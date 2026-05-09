package com.knowflow.parser.deadletter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.parser.config.ParseTaskMessagingProperties;
import com.knowflow.parser.deadletter.entity.DeadLetterMessageEntity;
import com.knowflow.parser.deadletter.mapper.DeadLetterMessageMapper;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.deadletter.vo.DeadLetterMessageVO;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.messaging.ParseTaskDispatchMessage;
import com.knowflow.parser.runtime.ParseTaskRuntimeSnapshot;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.service.ParseTaskService;
import com.knowflow.parser.vo.ParseTaskRuntimeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeadLetterMessageServiceImpl implements DeadLetterMessageService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterMessageServiceImpl.class);

    private final DeadLetterMessageMapper deadLetterMessageMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ParseTaskService parseTaskService;
    private final CurrentUserProvider currentUserProvider;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;
    private final ParseTaskMessagingProperties properties;
    private final ObjectMapper objectMapper;

    public DeadLetterMessageServiceImpl(DeadLetterMessageMapper deadLetterMessageMapper,
                                        ParseTaskMapper parseTaskMapper,
                                        KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        ParseTaskService parseTaskService,
                                        CurrentUserProvider currentUserProvider,
                                        ParseTaskRuntimeTracker parseTaskRuntimeTracker,
                                        ParseTaskMessagingProperties properties,
                                        ObjectMapper objectMapper) {
        this.deadLetterMessageMapper = deadLetterMessageMapper;
        this.parseTaskMapper = parseTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.parseTaskService = parseTaskService;
        this.currentUserProvider = currentUserProvider;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<DeadLetterMessageVO> page(Integer pageNo,
                                                  Integer pageSize,
                                                  String replayStatus,
                                                  String taskType,
                                                  Long taskId,
                                                  Long documentId,
                                                  String keyword) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Page<DeadLetterMessageEntity> page = deadLetterMessageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getTenantId, tenantId)
                        .eq(StringUtils.hasText(replayStatus), DeadLetterMessageEntity::getReplayStatus, replayStatus)
                        .eq(StringUtils.hasText(taskType), DeadLetterMessageEntity::getTaskType, taskType)
                        .eq(taskId != null, DeadLetterMessageEntity::getTaskId, taskId)
                        .eq(documentId != null, DeadLetterMessageEntity::getDocumentId, documentId)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(DeadLetterMessageEntity::getDeadLetterNo, keyword)
                                .or()
                                .like(DeadLetterMessageEntity::getTaskNo, keyword)
                                .or()
                                .like(DeadLetterMessageEntity::getErrorMessage, keyword))
                        .orderByDesc(DeadLetterMessageEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                enrich(page.getRecords())
        );
    }

    @Override
    public DeadLetterMessageVO getById(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        DeadLetterMessageEntity entity = deadLetterMessageMapper.selectOne(
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getId, id)
                        .eq(DeadLetterMessageEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Dead letter message not found");
        }
        return enrich(List.of(entity)).get(0);
    }

    @Override
    @Transactional
    public void replay(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        DeadLetterMessageEntity entity = deadLetterMessageMapper.selectOne(
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getId, id)
                        .eq(DeadLetterMessageEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Dead letter message not found");
        }
        ensureReplayable(entity);
        replayInternal(entity, REPLAY_STATUS_MANUAL_REPLAYED);
    }

    @Override
    @Transactional
    public void processAutoReplayBatch() {
        if (!properties.isAutoRetryEnabled() || !properties.isRabbitMode()) {
            return;
        }
        List<DeadLetterMessageEntity> entities = deadLetterMessageMapper.selectList(
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getReplayStatus, REPLAY_STATUS_READY)
                        .le(DeadLetterMessageEntity::getNextRetryAt, LocalDateTime.now())
                        .orderByAsc(DeadLetterMessageEntity::getNextRetryAt)
                        .last("limit " + properties.getAutoRetryBatchSize())
        );
        for (DeadLetterMessageEntity entity : entities) {
            try {
                replayInternal(entity, REPLAY_STATUS_AUTO_REPLAYED);
            } catch (Exception ex) {
                log.error("Auto replay failed. deadLetterId={}, taskId={}", entity.getId(), entity.getTaskId(), ex);
            }
        }
    }

    @Override
    @Transactional
    public void resolveByTaskId(Long taskId) {
        if (taskId == null) {
            return;
        }
        deadLetterMessageMapper.update(
                null,
                new LambdaUpdateWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getTaskId, taskId)
                        .isNull(DeadLetterMessageEntity::getResolvedAt)
                        .set(DeadLetterMessageEntity::getReplayStatus, REPLAY_STATUS_RESOLVED)
                        .set(DeadLetterMessageEntity::getResolvedAt, LocalDateTime.now())
                        .set(DeadLetterMessageEntity::getNextRetryAt, null)
        );
    }

    @Override
    @Transactional
    public void recordDeadLetter(ParseTaskDispatchMessage message, String sourceQueue, String sourceExchange, String routingKey) {
        ParseTaskEntity task = parseTaskMapper.selectById(message.taskId());
        KnowledgeDocumentEntity document = task == null ? null : knowledgeDocumentMapper.selectById(task.getDocumentId());
        int retryAttempt = task == null || task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        boolean canAutoRetry = properties.isAutoRetryEnabled() && retryAttempt <= properties.getMaxAutoRetries();

        DeadLetterMessageEntity entity = new DeadLetterMessageEntity();
        entity.setTenantId(task == null ? (document == null ? null : document.getTenantId()) : task.getTenantId());
        entity.setDeadLetterNo(CodeGenerator.deadLetterCode());
        entity.setTaskId(message.taskId());
        entity.setTaskType(task == null ? "UNKNOWN" : task.getTaskType());
        entity.setTaskNo(task == null ? null : task.getTaskNo());
        entity.setDocumentId(document == null ? null : document.getId());
        entity.setSourceQueue(sourceQueue);
        entity.setSourceExchange(sourceExchange);
        entity.setRoutingKey(routingKey);
        entity.setDeadLetterReason("TASK_FAILED");
        entity.setErrorMessage(task == null ? "Task failed and was dead-lettered" : task.getErrorMessage());
        entity.setPayloadJson(writePayload(message));
        entity.setRetryAttempt(retryAttempt);
        entity.setReplayStatus(canAutoRetry ? REPLAY_STATUS_READY : REPLAY_STATUS_MANUAL_REQUIRED);
        entity.setNextRetryAt(canAutoRetry
                ? LocalDateTime.now().plusSeconds(properties.getAutoRetryBaseDelaySeconds() * retryAttempt)
                : null);
        deadLetterMessageMapper.insert(entity);
        log.warn("Recorded dead letter. deadLetterNo={}, taskId={}, taskType={}, retryAttempt={}",
                entity.getDeadLetterNo(),
                entity.getTaskId(),
                entity.getTaskType(),
                entity.getRetryAttempt());
    }

    @Scheduled(fixedDelayString = "${knowflow.parser.messaging.auto-retry-interval-ms:15000}")
    public void scheduledAutoReplay() {
        processAutoReplayBatch();
    }

    private void replayInternal(DeadLetterMessageEntity entity, String replayStatus) {
        parseTaskService.retrySystem(entity.getTaskId());
        entity.setReplayStatus(replayStatus);
        entity.setReplayedAt(LocalDateTime.now());
        entity.setReplayMode(REPLAY_STATUS_AUTO_REPLAYED.equals(replayStatus) ? "AUTO" : "MANUAL");
        entity.setNextRetryAt(null);
        deadLetterMessageMapper.updateById(entity);
    }

    private void ensureReplayable(DeadLetterMessageEntity entity) {
        if (entity.getResolvedAt() != null || REPLAY_STATUS_RESOLVED.equals(entity.getReplayStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Dead letter message has already been resolved");
        }
        if (REPLAY_STATUS_AUTO_REPLAYED.equals(entity.getReplayStatus())
                || REPLAY_STATUS_MANUAL_REPLAYED.equals(entity.getReplayStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Dead letter message has already been replayed");
        }
    }

    private List<DeadLetterMessageVO> enrich(List<DeadLetterMessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Map<Long, ParseTaskEntity> taskMap = new HashMap<>();
        Map<Long, KnowledgeDocumentEntity> documentMap = new HashMap<>();
        for (Long taskId : entities.stream().map(DeadLetterMessageEntity::getTaskId).distinct().toList()) {
            ParseTaskEntity task = parseTaskMapper.selectById(taskId);
            if (task != null) {
                taskMap.put(taskId, task);
                KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
                if (document != null) {
                    documentMap.put(document.getId(), document);
                }
            }
        }
        return entities.stream().map(entity -> toVO(entity, taskMap, documentMap)).toList();
    }

    private DeadLetterMessageVO toVO(DeadLetterMessageEntity entity,
                                     Map<Long, ParseTaskEntity> taskMap,
                                     Map<Long, KnowledgeDocumentEntity> documentMap) {
        ParseTaskEntity task = taskMap.get(entity.getTaskId());
        KnowledgeDocumentEntity document = entity.getDocumentId() == null ? null : documentMap.get(entity.getDocumentId());
        return DeadLetterMessageVO.builder()
                .id(entity.getId())
                .deadLetterNo(entity.getDeadLetterNo())
                .tenantId(entity.getTenantId())
                .taskId(entity.getTaskId())
                .taskType(entity.getTaskType())
                .taskNo(entity.getTaskNo())
                .taskStatus(task == null ? null : task.getStatus())
                .documentId(entity.getDocumentId())
                .documentName(document == null ? null : document.getDocName())
                .sourceQueue(entity.getSourceQueue())
                .sourceExchange(entity.getSourceExchange())
                .routingKey(entity.getRoutingKey())
                .deadLetterReason(entity.getDeadLetterReason())
                .errorMessage(entity.getErrorMessage())
                .payloadJson(entity.getPayloadJson())
                .retryAttempt(entity.getRetryAttempt())
                .replayStatus(entity.getReplayStatus())
                .nextRetryAt(entity.getNextRetryAt())
                .replayedAt(entity.getReplayedAt())
                .resolvedAt(entity.getResolvedAt())
                .replayMode(entity.getReplayMode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .taskRuntime(toRuntimeVO(parseTaskRuntimeTracker.getSnapshot(entity.getTaskId())))
                .build();
    }

    private ParseTaskRuntimeVO toRuntimeVO(ParseTaskRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return ParseTaskRuntimeVO.builder()
                .transport(snapshot.getTransport())
                .queueStatus(snapshot.getQueueStatus())
                .workerId(snapshot.getWorkerId())
                .queuedAt(snapshot.getQueuedAt())
                .dequeuedAt(snapshot.getDequeuedAt())
                .lastHeartbeatAt(snapshot.getLastHeartbeatAt())
                .queueLatencyMs(snapshot.getQueueLatencyMs())
                .chunkCount(snapshot.getChunkCount())
                .durationMs(snapshot.getDurationMs())
                .errorMessage(snapshot.getErrorMessage())
                .build();
    }

    private String writePayload(ParseTaskDispatchMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            return "{\"taskId\":" + message.taskId() + "}";
        }
    }
}
