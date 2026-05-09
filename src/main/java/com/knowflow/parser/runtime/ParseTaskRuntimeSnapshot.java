package com.knowflow.parser.runtime;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ParseTaskRuntimeSnapshot {

    private final String transport;
    private final String queueStatus;
    private final String workerId;
    private final LocalDateTime queuedAt;
    private final LocalDateTime dequeuedAt;
    private final LocalDateTime lastHeartbeatAt;
    private final Long queueLatencyMs;
    private final Integer chunkCount;
    private final Long durationMs;
    private final String errorMessage;
}
