package com.knowflow.parser.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParseTaskVO {

    private Long id;
    private Long tenantId;
    private Long documentId;
    private String taskNo;
    private String taskType;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ParseTaskRuntimeVO runtime;
}
