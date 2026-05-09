package com.knowflow.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateQaSessionRequest {

    @NotNull(message = "knowledgeBaseId cannot be null")
    private Long knowledgeBaseId;

    @NotBlank(message = "sessionTitle cannot be empty")
    private String sessionTitle;
}

