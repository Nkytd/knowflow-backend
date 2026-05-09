package com.knowflow.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("knowledge_document")
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
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
}

