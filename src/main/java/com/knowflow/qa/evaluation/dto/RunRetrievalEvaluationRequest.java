package com.knowflow.qa.evaluation.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class RunRetrievalEvaluationRequest {

    private Long knowledgeBaseId;
    private List<Long> caseIds;

    @Min(value = 1, message = "topK must be greater than 0")
    private Integer topK;
}
