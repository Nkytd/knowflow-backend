package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeBaseFailedDocumentVO {

    private Long documentId;
    private String docCode;
    private String docName;
    private String status;
    private String parseStatus;
    private String indexStatus;
    private Integer chunkCount;
    private Long latestTaskId;
    private String latestTaskType;
    private String latestTaskStatus;
    private Integer retryCount;
    private String errorMessage;
    private Integer deadLetterCount;
    private LocalDateTime updatedAt;
}
