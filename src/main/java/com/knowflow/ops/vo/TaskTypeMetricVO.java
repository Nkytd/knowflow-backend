package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskTypeMetricVO {

    private String taskType;
    private Long totalCount;
    private Long successCount;
    private Long failedCount;
    private Long processingCount;
    private Double failureRate;
    private Double successRate;
    private Long avgDurationMs;
    private Long p95DurationMs;
}
