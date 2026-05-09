package com.knowflow.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("knowledge_chunk")
@EqualsAndHashCode(callSuper = true)
public class KnowledgeChunkEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkNo;
    private String content;
    private Integer charCount;
    private Integer tokenCount;
    private Integer sourcePage;
    private String sourceSection;
    private String status;
}

