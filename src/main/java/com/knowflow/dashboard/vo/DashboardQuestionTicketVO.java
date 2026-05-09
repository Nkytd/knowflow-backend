package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DashboardQuestionTicketVO {

    private Long id;
    private String ticketNo;
    private Long sourceQaMessageId;
    private String title;
    private String priority;
    private String status;
    private String assigneeName;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
