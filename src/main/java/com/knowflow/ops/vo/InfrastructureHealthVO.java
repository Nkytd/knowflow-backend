package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InfrastructureHealthVO {

    private String overallStatus;
    private String summary;
    private LocalDateTime generatedAt;
    private List<InfrastructureComponentHealthVO> components;
}
