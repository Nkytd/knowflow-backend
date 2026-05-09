package com.knowflow.parser.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.parser.config.ParseTaskMessagingProperties;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.messaging.ParseTaskDispatchGateway;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.support.ParseTaskStateMachine;
import com.knowflow.parser.support.TaskWorkerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "rabbit")
public class ParseTaskRecoveryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskRecoveryBootstrap.class);

    private final ParseTaskMapper parseTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ParseTaskDispatchGateway parseTaskDispatchGateway;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;
    private final ParseTaskMessagingProperties properties;
    private final ParseTaskGovernanceEventService governanceEventService;

    public ParseTaskRecoveryBootstrap(ParseTaskMapper parseTaskMapper,
                                      KnowledgeDocumentMapper knowledgeDocumentMapper,
                                      ParseTaskDispatchGateway parseTaskDispatchGateway,
                                      ParseTaskRuntimeTracker parseTaskRuntimeTracker,
                                      ParseTaskMessagingProperties properties,
                                      ParseTaskGovernanceEventService governanceEventService) {
        this.parseTaskMapper = parseTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.parseTaskDispatchGateway = parseTaskDispatchGateway;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
        this.properties = properties;
        this.governanceEventService = governanceEventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        Set<Long> taskIdsToDispatch = new LinkedHashSet<>();
        taskIdsToDispatch.addAll(findPendingTaskIds());
        taskIdsToDispatch.addAll(recoverStaleProcessingTaskIds());

        if (taskIdsToDispatch.isEmpty()) {
            log.info("No async tasks need startup recovery");
            return;
        }

        log.info("Startup recovery will redispatch {} async tasks", taskIdsToDispatch.size());
        for (Long taskId : taskIdsToDispatch) {
            try {
                parseTaskDispatchGateway.dispatch(taskId);
            } catch (Exception ex) {
                log.error("Failed to redispatch async task during startup recovery. taskId={}", taskId, ex);
            }
        }
    }

    private List<Long> findPendingTaskIds() {
        return parseTaskMapper.selectList(
                        new LambdaQueryWrapper<ParseTaskEntity>()
                                .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PENDING)
                                .orderByAsc(ParseTaskEntity::getCreatedAt)
                                .last("limit " + properties.getStartupRecoveryLimit())
                ).stream()
                .map(ParseTaskEntity::getId)
                .toList();
    }

    private List<Long> recoverStaleProcessingTaskIds() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(properties.getStaleProcessingMinutes());
        List<ParseTaskEntity> staleTasks = parseTaskMapper.selectList(
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .le(ParseTaskEntity::getStartedAt, staleThreshold)
                        .orderByAsc(ParseTaskEntity::getStartedAt)
                        .last("limit " + properties.getStartupRecoveryLimit())
        );

        List<Long> recoveredTaskIds = new ArrayList<>();
        for (ParseTaskEntity task : staleTasks) {
            int updatedRows = parseTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<ParseTaskEntity>()
                            .eq(ParseTaskEntity::getId, task.getId())
                            .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                            .eq(ParseTaskEntity::getStartedAt, task.getStartedAt())
                            .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.PENDING)
                            .set(ParseTaskEntity::getStartedAt, null)
                            .set(ParseTaskEntity::getFinishedAt, null)
                            .set(ParseTaskEntity::getDurationMs, null)
                            .set(ParseTaskEntity::getErrorMessage, null)
            );
            if (updatedRows <= 0) {
                governanceEventService.record(task,
                        ParseTaskGovernanceEventService.STARTUP_RECOVERY_SKIPPED,
                        "Startup recovery skipped this task because task state changed after scan.",
                        null,
                        task.getStartedAt());
                log.info("Skip stale task recovery because task state changed. taskId={}", task.getId());
                continue;
            }
            recoveredTaskIds.add(task.getId());
            governanceEventService.record(task,
                    ParseTaskGovernanceEventService.STARTUP_STALE_TASK_RECOVERED,
                    "Startup recovery reset stale PROCESSING task to PENDING and will redispatch it.",
                    null,
                    task.getStartedAt());
            parseTaskRuntimeTracker.forceReleaseWorkerLock(task.getId());

            KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(task.getDocumentId());
            if (document != null) {
                if (TaskWorkerRouter.TASK_TYPE_PARSE.equals(task.getTaskType())) {
                    document.setParseStatus(ParseTaskStateMachine.PENDING);
                    document.setIndexStatus(ParseTaskStateMachine.PENDING);
                } else if (TaskWorkerRouter.TASK_TYPE_INDEX_VECTOR.equals(task.getTaskType())) {
                    document.setIndexStatus(ParseTaskStateMachine.PENDING);
                }
                knowledgeDocumentMapper.updateById(document);
            }
        }
        return recoveredTaskIds;
    }
}
