package com.knowflow.ticket.dto;

import lombok.Data;

@Data
public class CreateQaHandoffTicketRequest {

    private String title;
    private String content;
    private String priority;
}
