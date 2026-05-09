package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseFailureReasonVO {

    private String reason;
    private String taskType;
    private Integer documentCount;
    private Long sampleDocumentId;
    private String sampleDocumentName;
}
