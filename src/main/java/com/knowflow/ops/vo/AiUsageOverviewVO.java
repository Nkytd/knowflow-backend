package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiUsageOverviewVO {

    private Integer days;
    private Long totalCallCount;
    private Long successCallCount;
    private Long failedCallCount;
    private Long noHitCount;
    private Long handoffCount;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
    private Long avgLatencyMs;
    private Long avgRetrievalLatencyMs;
    private Long avgGenerationLatencyMs;
    private Double failureRate;
    private Double cacheHitRate;
    private BigDecimal estimatedCostCny;
    private LocalDateTime generatedAt;
    private List<AiModelUsageMetricVO> modelMetrics;
}
