package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DashboardQuestionDetailVO {

    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String questionText;
    private Long askCount;
    private Long successCount;
    private Long noHitCount;
    private Long handoffCount;
    private Long relatedTicketCount;
    private Long resolvedTicketCount;
    private Long relatedDraftCount;
    private Long publishedDraftCount;
    private LocalDateTime latestAskedAt;
    private String suggestedAction;
    private List<DashboardQuestionMessageVO> messages;
    private List<DashboardQuestionTicketVO> tickets;
    private List<DashboardQuestionDraftVO> drafts;
}
