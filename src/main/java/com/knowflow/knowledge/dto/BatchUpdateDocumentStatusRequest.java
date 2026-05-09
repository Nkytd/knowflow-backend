package com.knowflow.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchUpdateDocumentStatusRequest {

    @NotEmpty(message = "Document ids must not be empty")
    private List<@NotNull Long> documentIds;

    @NotBlank(message = "Status must not be blank")
    private String status;
}
