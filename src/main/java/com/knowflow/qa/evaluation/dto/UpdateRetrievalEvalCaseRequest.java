package com.knowflow.qa.evaluation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRetrievalEvalCaseRequest {

    @NotBlank(message = "caseName is required")
    private String caseName;

    @NotBlank(message = "questionText is required")
    private String questionText;

    private String expectedStatus = "SUCCESS";
    private Long expectedDocumentId;
    private String expectedDocumentName;
    private List<String> expectedKeywords;

    @Min(value = 1, message = "topK must be greater than 0")
    private Integer topK = 5;

    private Boolean enabled = true;
    private String remark;
}
