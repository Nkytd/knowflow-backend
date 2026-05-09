package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AiModelUsageMetricVO {

    private String modelName;
    private Long callCount;
    private Long successCount;
    private Long failedCount;
    private Long noHitCount;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
    private Long avgLatencyMs;
    private Long avgGenerationLatencyMs;
    private Double failureRate;
    private BigDecimal estimatedCostCny;
}
