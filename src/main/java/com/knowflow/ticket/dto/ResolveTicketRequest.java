package com.knowflow.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveTicketRequest {

    @NotBlank(message = "solution cannot be empty")
    private String solution;
}
