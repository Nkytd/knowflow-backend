package com.knowflow.backflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateKnowledgeDraftRequest {

    @NotNull(message = "knowledgeBaseId cannot be null")
    private Long knowledgeBaseId;

    @NotBlank(message = "draftType cannot be empty")
    private String draftType;

    @NotBlank(message = "title cannot be empty")
    private String title;

    @NotBlank(message = "questionText cannot be empty")
    private String questionText;

    @NotBlank(message = "answerText cannot be empty")
    private String answerText;
}
