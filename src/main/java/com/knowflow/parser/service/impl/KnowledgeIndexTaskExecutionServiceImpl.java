package com.knowflow.parser.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.service.KnowledgeIndexTaskExecutionService;
import com.knowflow.parser.support.ParseTaskStateMachine;
import com.knowflow.parser.support.TaskWorkerRouter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KnowledgeIndexTaskExecutionServiceImpl implements KnowledgeIndexTaskExecutionService {

    private final ParseTaskMapper parseTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DeadLetterMessageService deadLetterMessageService;
    private final ParseTaskGovernanceEventService governanceEventService;

    public KnowledgeIndexTaskExecutionServiceImpl(ParseTaskMapper parseTaskMapper,
                                                  KnowledgeDocumentMapper knowledgeDocumentMapper,
                                                  DeadLetterMessageService deadLetterMessageService,
                                                  ParseTaskGovernanceEventService governanceEventService) {
        this.parseTaskMapper = parseTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.deadLetterMessageService = deadLetterMessageService;
        this.governanceEventService = governanceEventService;
    }

    @Override
    @Transactional
    public IndexExecutionContext startProcessing(Long taskId) {
        ParseTaskEntity existingTask = parseTaskMapper.selectById(taskId);
        if (existingTask == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Index task not found");
        }
        if (!TaskWorkerRouter.TASK_TYPE_INDEX_VECTOR.equals(existingTask.getTaskType())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Task is not an index task");
        }
        if (!ParseTaskStateMachine.canStart(existingTask.getStatus())) {
            return null;
        }

        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(existingTask.getDocumentId());
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document not found for index task");
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
        document.setIndexStatus(ParseTaskStateMachine.PROCESSING);
        knowledgeDocumentMapper.updateById(document);
        return new IndexExecutionContext(task, document);
    }

    @Override
    @Transactional
    public void completeSuccess(Long taskId, LocalDateTime attemptStartedAt, int indexedChunkCount, long durationMs) {
        ParseTaskEntity task = getCompletableTask(taskId, attemptStartedAt);
        if (task == null) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Index success result was ignored because the attempt is no longer completable.",
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
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.SUCCESS)
                        .set(ParseTaskEntity::getFinishedAt, LocalDateTime.now())
                        .set(ParseTaskEntity::getDurationMs, durationMs)
                        .set(ParseTaskEntity::getErrorMessage, null)
        );
        if (completed == 0) {
            governanceEventService.record(task,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Index success result was ignored because task state changed before completion update.",
                    null,
                    attemptStartedAt);
            return;
        }
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
        if (document != null) {
            document.setIndexStatus(ParseTaskStateMachine.SUCCESS);
            document.setChunkCount(indexedChunkCount);
            knowledgeDocumentMapper.updateById(document);
        }

        deadLetterMessageService.resolveByTaskId(taskId);
    }

    @Override
    @Transactional
    public void completeFailure(Long taskId, LocalDateTime attemptStartedAt, String errorMessage, long durationMs) {
        ParseTaskEntity task = getCompletableTask(taskId, attemptStartedAt);
        if (task == null) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Index failure result was ignored because the attempt is no longer completable.",
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
                        .set(ParseTaskEntity::getErrorMessage, truncate(errorMessage))
        );
        if (completed == 0) {
            governanceEventService.record(task,
                    ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                    "Index failure result was ignored because task state changed before completion update.",
                    null,
                    attemptStartedAt);
            return;
        }
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
        if (document != null) {
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

    private String truncate(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
