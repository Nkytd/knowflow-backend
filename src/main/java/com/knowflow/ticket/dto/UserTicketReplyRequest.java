package com.knowflow.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserTicketReplyRequest {

    @NotBlank(message = "content cannot be empty")
    private String content;
}
