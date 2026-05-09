package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgeBaseVO {

    private Long id;
    private Long tenantId;
    private String kbCode;
    private String kbName;
    private String description;
    private String status;
    private Integer docCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private KnowledgeBaseStatsVO stats;
    private List<KnowledgeBaseFailureReasonVO> topFailureReasons;
    private List<KnowledgeBaseFailedDocumentVO> failedDocuments;
}
