package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseStatsVO {

    private Integer totalDocuments;
    private Integer enabledDocuments;
    private Integer disabledDocuments;
    private Integer parsePendingCount;
    private Integer parseProcessingCount;
    private Integer parseSuccessCount;
    private Integer parseFailedCount;
    private Integer indexPendingCount;
    private Integer indexProcessingCount;
    private Integer indexSuccessCount;
    private Integer indexFailedCount;
    private Integer failedDocumentCount;
    private Integer totalChunks;
    private Integer openDeadLetterCount;
}
