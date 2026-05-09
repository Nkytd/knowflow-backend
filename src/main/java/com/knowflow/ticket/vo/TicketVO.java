package com.knowflow.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketVO {

    private Long id;
    private String ticketNo;
    private String sourceType;
    private Long sourceQaMessageId;
    private Long reporterUserId;
    private String reporterName;
    private Long assigneeUserId;
    private String assigneeName;
    private String title;
    private String content;
    private String priority;
    private String status;
    private String channel;
    private String slaPolicy;
    private LocalDateTime slaDueAt;
    private String slaStatus;
    private LocalDateTime slaBreachedAt;
    private LocalDateTime slaReminderSentAt;
    private Boolean slaBreached;
    private Long slaRemainingMinutes;
    private String sourceQuestionText;
    private String sourceAnswerText;
    private Long relatedDraftId;
    private String relatedDraftStatus;
    private String relatedDraftTitle;
    private LocalDateTime lastReplyAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
