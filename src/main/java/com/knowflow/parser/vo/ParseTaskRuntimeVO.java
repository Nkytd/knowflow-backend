package com.knowflow.parser.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParseTaskRuntimeVO {

    private String transport;
    private String queueStatus;
    private String workerId;
    private LocalDateTime queuedAt;
    private LocalDateTime dequeuedAt;
    private LocalDateTime lastHeartbeatAt;
    private Long queueLatencyMs;
    private Integer chunkCount;
    private Long durationMs;
    private String errorMessage;
}
