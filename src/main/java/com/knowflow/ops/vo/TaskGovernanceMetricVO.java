package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskGovernanceMetricVO {

    private Long totalEventCount;
    private Long duplicateConsumptionSkippedCount;
    private Long nonPendingMessageSkippedCount;
    private Long staleAttemptCompletionSkippedCount;
    private Long startupStaleTaskRecoveredCount;
    private Long startupRecoverySkippedCount;
}
