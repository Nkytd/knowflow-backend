package com.knowflow.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddTicketCommentRequest {

    @NotBlank(message = "commentType cannot be empty")
    private String commentType;

    @NotBlank(message = "content cannot be empty")
    private String content;

    private Boolean visibleToUser;
}
