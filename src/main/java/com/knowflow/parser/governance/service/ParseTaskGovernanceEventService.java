package com.knowflow.parser.governance.service;

import com.knowflow.parser.entity.ParseTaskEntity;

import java.time.LocalDateTime;

public interface ParseTaskGovernanceEventService {

    String DUPLICATE_CONSUMPTION_SKIPPED = "DUPLICATE_CONSUMPTION_SKIPPED";
    String NON_PENDING_MESSAGE_SKIPPED = "NON_PENDING_MESSAGE_SKIPPED";
    String STALE_ATTEMPT_COMPLETION_SKIPPED = "STALE_ATTEMPT_COMPLETION_SKIPPED";
    String STARTUP_STALE_TASK_RECOVERED = "STARTUP_STALE_TASK_RECOVERED";
    String STARTUP_RECOVERY_SKIPPED = "STARTUP_RECOVERY_SKIPPED";

    void record(Long taskId, String eventType, String reason, String workerId, LocalDateTime attemptStartedAt);

    void record(ParseTaskEntity task, String eventType, String reason, String workerId, LocalDateTime attemptStartedAt);
}
