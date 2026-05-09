package com.knowflow.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignTicketRequest {

    @NotNull(message = "assigneeUserId cannot be null")
    private Long assigneeUserId;

    private String remark;
}
