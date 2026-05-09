package com.knowflow.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketCommentVO {

    private Long id;
    private Long ticketId;
    private String commentType;
    private Long commentUserId;
    private String commentUserName;
    private String content;
    private Boolean visibleToUser;
    private LocalDateTime createdAt;
}
