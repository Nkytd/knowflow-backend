package com.knowflow.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@TableName("knowledge_chunk_index")
@EqualsAndHashCode(callSuper = true)
public class KnowledgeChunkIndexEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Long chunkId;
    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDim;
    private BigDecimal vectorNorm;
    private String embeddingJson;
    private String status;
}
