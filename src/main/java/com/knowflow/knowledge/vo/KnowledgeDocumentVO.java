package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocumentVO {

    private Long id;
    private Long tenantId;
    private Long knowledgeBaseId;
    private String docCode;
    private String docName;
    private String sourceType;
    private String storageType;
    private String storagePath;
    private String fileType;
    private Long fileSize;
    private Integer versionNo;
    private String status;
    private String parseStatus;
    private String indexStatus;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

