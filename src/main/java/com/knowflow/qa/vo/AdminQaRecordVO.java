package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminQaRecordVO {

    private Long id;
    private Long sessionId;
    private String sessionNo;
    private String sessionTitle;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private Long userId;
    private String username;
    private String realName;
    private String questionText;
    private String answerText;
    private String answerStatus;
    private String modelName;
    private Long latencyMs;
    private Long retrievalLatencyMs;
    private Long generationLatencyMs;
    private Boolean retrievalCacheHit;
    private String answerMode;
    private Integer sourceCount;
    private Boolean needHumanHandoff;
    private Long ticketId;
    private String ticketNo;
    private String ticketStatus;
    private Long draftId;
    private String draftStatus;
    private LocalDateTime createdAt;
}
