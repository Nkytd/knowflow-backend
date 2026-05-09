package com.knowflow.knowledge.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteDocumentsRequest {

    @NotEmpty(message = "Document ids must not be empty")
    private List<@NotNull Long> documentIds;
}
