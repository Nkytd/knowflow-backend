package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QaMessageVO {

    private Long id;
    private Long sessionId;
    private String questionText;
    private String answerText;
    private String answerStatus;
    private String modelName;
    private Long latencyMs;
    private Long retrievalLatencyMs;
    private Long generationLatencyMs;
    private Boolean retrievalCacheHit;
    private String answerMode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer sourceCount;
    private Boolean needHumanHandoff;
    private LocalDateTime createdAt;
    private List<RetrievalRecordVO> sources;
}

