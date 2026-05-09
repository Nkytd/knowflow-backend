package com.knowflow.qa.evaluation.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RetrievalEvalCaseVO {

    private Long id;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String caseName;
    private String questionText;
    private String expectedStatus;
    private Long expectedDocumentId;
    private String expectedDocumentName;
    private List<String> expectedKeywords;
    private Integer topK;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
}
