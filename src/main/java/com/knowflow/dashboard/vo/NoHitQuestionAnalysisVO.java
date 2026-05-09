package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NoHitQuestionAnalysisVO {

    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String questionText;
    private Long askCount;
    private Long handoffCount;
    private Long relatedTicketCount;
    private Long resolvedTicketCount;
    private Long relatedDraftCount;
    private Long publishedDraftCount;
    private LocalDateTime latestAskedAt;
    private String suggestedAction;
}
