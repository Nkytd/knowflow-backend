package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DashboardQuestionDraftVO {

    private Long id;
    private Long sourceTicketId;
    private String title;
    private String draftType;
    private String status;
    private String reviewerName;
    private Long publishedDocumentId;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
