package com.knowflow.parser.deadletter.vo;

import com.knowflow.parser.vo.ParseTaskRuntimeVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeadLetterMessageVO {

    private Long id;
    private String deadLetterNo;
    private Long tenantId;
    private Long taskId;
    private String taskType;
    private String taskNo;
    private String taskStatus;
    private Long documentId;
    private String documentName;
    private String sourceQueue;
    private String sourceExchange;
    private String routingKey;
    private String deadLetterReason;
    private String errorMessage;
    private String payloadJson;
    private Integer retryAttempt;
    private String replayStatus;
    private LocalDateTime nextRetryAt;
    private LocalDateTime replayedAt;
    private LocalDateTime resolvedAt;
    private String replayMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ParseTaskRuntimeVO taskRuntime;
}
