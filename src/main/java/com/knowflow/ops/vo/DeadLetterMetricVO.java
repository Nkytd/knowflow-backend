package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeadLetterMetricVO {

    private Long totalCount;
    private Long unresolvedCount;
    private Long readyCount;
    private Long manualRequiredCount;
    private Long replayedCount;
    private Long resolvedCount;
}
