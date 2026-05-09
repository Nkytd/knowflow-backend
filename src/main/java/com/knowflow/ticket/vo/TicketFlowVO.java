package com.knowflow.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketFlowVO {

    private Long id;
    private Long ticketId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String operatorUserName;
    private String remark;
    private LocalDateTime createdAt;
}
