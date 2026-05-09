package com.knowflow.parser.governance.service.impl;

import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.entity.ParseTaskGovernanceEventEntity;
import com.knowflow.parser.governance.mapper.ParseTaskGovernanceEventMapper;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ParseTaskGovernanceEventServiceImpl implements ParseTaskGovernanceEventService {

    private static final int REASON_MAX_LENGTH = 500;
    private static final int WORKER_ID_MAX_LENGTH = 128;

    private final ParseTaskGovernanceEventMapper eventMapper;
    private final ParseTaskMapper parseTaskMapper;

    public ParseTaskGovernanceEventServiceImpl(ParseTaskGovernanceEventMapper eventMapper,
                                               ParseTaskMapper parseTaskMapper) {
        this.eventMapper = eventMapper;
        this.parseTaskMapper = parseTaskMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long taskId, String eventType, String reason, String workerId, LocalDateTime attemptStartedAt) {
        ParseTaskEntity task = taskId == null ? null : parseTaskMapper.selectById(taskId);
        recordInternal(task, taskId, eventType, reason, workerId, attemptStartedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ParseTaskEntity task, String eventType, String reason, String workerId, LocalDateTime attemptStartedAt) {
        recordInternal(task, task == null ? null : task.getId(), eventType, reason, workerId, attemptStartedAt);
    }

    private void recordInternal(ParseTaskEntity task,
                                Long taskId,
                                String eventType,
                                String reason,
                                String workerId,
                                LocalDateTime attemptStartedAt) {
        if (!StringUtils.hasText(eventType)) {
            return;
        }
        ParseTaskGovernanceEventEntity entity = new ParseTaskGovernanceEventEntity();
        if (task != null) {
            entity.setTenantId(task.getTenantId());
            entity.setTaskId(task.getId());
            entity.setDocumentId(task.getDocumentId());
            entity.setTaskNo(task.getTaskNo());
            entity.setTaskType(task.getTaskType());
        } else {
            entity.setTaskId(taskId);
        }
        entity.setEventType(eventType);
        entity.setReason(truncate(reason, REASON_MAX_LENGTH));
        entity.setWorkerId(truncate(workerId, WORKER_ID_MAX_LENGTH));
        entity.setAttemptStartedAt(attemptStartedAt);
        eventMapper.insert(entity);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
