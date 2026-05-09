package com.knowflow.parser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.parser.event.ParseTaskSubmittedEvent;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.runtime.ParseTaskRuntimeSnapshot;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.service.ParseTaskService;
import com.knowflow.parser.support.ParseTaskStateMachine;
import com.knowflow.parser.support.TaskWorkerRouter;
import com.knowflow.parser.vo.ParseTaskRuntimeVO;
import com.knowflow.parser.vo.ParseTaskVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ParseTaskServiceImpl implements ParseTaskService {

    private final ParseTaskMapper parseTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;

    public ParseTaskServiceImpl(ParseTaskMapper parseTaskMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                CurrentUserProvider currentUserProvider,
                                ApplicationEventPublisher applicationEventPublisher,
                                ParseTaskRuntimeTracker parseTaskRuntimeTracker) {
        this.parseTaskMapper = parseTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.currentUserProvider = currentUserProvider;
        this.applicationEventPublisher = applicationEventPublisher;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
    }

    @Override
    @Transactional
    public void createPendingParseTask(KnowledgeDocumentEntity documentEntity) {
        createPendingTask(documentEntity, TaskWorkerRouter.TASK_TYPE_PARSE);
    }

    @Override
    @Transactional
    public void createPendingIndexTask(KnowledgeDocumentEntity documentEntity) {
        createPendingTask(documentEntity, TaskWorkerRouter.TASK_TYPE_INDEX_VECTOR);
    }

    private void createPendingTask(KnowledgeDocumentEntity documentEntity, String taskType) {
        ParseTaskEntity taskEntity = new ParseTaskEntity();
        taskEntity.setTenantId(documentEntity.getTenantId());
        taskEntity.setDocumentId(documentEntity.getId());
        taskEntity.setTaskNo(CodeGenerator.parseTaskCode());
        taskEntity.setTaskType(taskType);
        taskEntity.setStatus(ParseTaskStateMachine.PENDING);
        taskEntity.setRetryCount(0);
        parseTaskMapper.insert(taskEntity);
        applicationEventPublisher.publishEvent(new ParseTaskSubmittedEvent(taskEntity.getId()));
    }

    @Override
    public PageResponse<ParseTaskVO> page(Integer pageNo, Integer pageSize, Long documentId, String status, String taskType) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Page<ParseTaskEntity> page = parseTaskMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getTenantId, tenantId)
                        .eq(documentId != null, ParseTaskEntity::getDocumentId, documentId)
                        .eq(StringUtils.hasText(status), ParseTaskEntity::getStatus, status)
                        .eq(StringUtils.hasText(taskType), ParseTaskEntity::getTaskType, taskType)
                        .orderByDesc(ParseTaskEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public ParseTaskVO getById(Long id) {
        return toVO(getEntityById(currentUserProvider.getCurrentUser().tenantId(), id));
    }

    @Override
    @Transactional
    public void retry(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        ParseTaskEntity taskEntity = getEntityById(tenantId, id);
        resetAndDispatch(taskEntity);
    }

    @Override
    @Transactional
    public void retrySystem(Long taskId) {
        ParseTaskEntity taskEntity = parseTaskMapper.selectById(taskId);
        if (taskEntity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Task not found");
        }
        resetAndDispatch(taskEntity);
    }

    @Override
    public Long findLatestTaskIdByDocumentId(Long documentId) {
        ParseTaskEntity entity = parseTaskMapper.selectOne(
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getDocumentId, documentId)
                        .orderByDesc(ParseTaskEntity::getCreatedAt)
                        .last("limit 1")
        );
        return entity == null ? null : entity.getId();
    }

    private ParseTaskEntity getEntityById(Long tenantId, Long id) {
        ParseTaskEntity entity = parseTaskMapper.selectOne(
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getTenantId, tenantId)
                        .eq(ParseTaskEntity::getId, id)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "解析任务不存在");
        }
        return entity;
    }

    private void resetAndDispatch(ParseTaskEntity taskEntity) {
        if (!ParseTaskStateMachine.canRetry(taskEntity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only failed tasks can be retried");
        }
        taskEntity.setStatus(ParseTaskStateMachine.PENDING);
        taskEntity.setRetryCount(taskEntity.getRetryCount() == null ? 1 : taskEntity.getRetryCount() + 1);
        taskEntity.setErrorMessage(null);
        taskEntity.setStartedAt(null);
        taskEntity.setFinishedAt(null);
        taskEntity.setDurationMs(null);
        parseTaskMapper.updateById(taskEntity);
        parseTaskRuntimeTracker.clearRuntime(taskEntity.getId());

        KnowledgeDocumentEntity documentEntity = knowledgeDocumentMapper.selectById(taskEntity.getDocumentId());
        if (documentEntity != null) {
            if (TaskWorkerRouter.TASK_TYPE_PARSE.equals(taskEntity.getTaskType())) {
                documentEntity.setParseStatus(ParseTaskStateMachine.PENDING);
                documentEntity.setIndexStatus(ParseTaskStateMachine.PENDING);
            } else if (TaskWorkerRouter.TASK_TYPE_INDEX_VECTOR.equals(taskEntity.getTaskType())) {
                documentEntity.setIndexStatus(ParseTaskStateMachine.PENDING);
            }
            knowledgeDocumentMapper.updateById(documentEntity);
        }

        applicationEventPublisher.publishEvent(new ParseTaskSubmittedEvent(taskEntity.getId()));
    }

    private ParseTaskVO toVO(ParseTaskEntity entity) {
        return ParseTaskVO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .documentId(entity.getDocumentId())
                .taskNo(entity.getTaskNo())
                .taskType(entity.getTaskType())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .durationMs(entity.getDurationMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .runtime(toRuntimeVO(parseTaskRuntimeTracker.getSnapshot(entity.getId())))
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
}
