package com.knowflow.backflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateKnowledgeDraftFromTicketRequest {

    @NotNull(message = "knowledgeBaseId cannot be null")
    private Long knowledgeBaseId;

    private String draftType;

    private String title;
}
