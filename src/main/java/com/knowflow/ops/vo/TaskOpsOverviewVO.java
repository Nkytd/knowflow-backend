package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskOpsOverviewVO {

    private Integer days;
    private Long totalTaskCount;
    private Long successTaskCount;
    private Long failedTaskCount;
    private Long processingTaskCount;
    private Long pendingTaskCount;
    private Long staleProcessingTaskCount;
    private Double failureRate;
    private Double successRate;
    private Long avgDurationMs;
    private Long p95DurationMs;
    private Integer healthScore;
    private String healthLevel;
    private String healthSummary;
    private LocalDateTime generatedAt;
    private List<TaskStatusMetricVO> statusMetrics;
    private List<TaskTypeMetricVO> taskTypeMetrics;
    private DeadLetterMetricVO deadLetterMetrics;
    private TaskGovernanceMetricVO governanceMetrics;
    private List<OpsHealthIssueVO> healthIssues;
}
