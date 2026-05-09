package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskStatusMetricVO {

    private String status;
    private Long count;
}
