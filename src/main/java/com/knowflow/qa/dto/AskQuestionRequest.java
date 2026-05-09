package com.knowflow.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AskQuestionRequest {

    @NotNull(message = "sessionId cannot be null")
    private Long sessionId;

    @NotBlank(message = "question cannot be empty")
    private String question;
    private String modelPreference;
}

