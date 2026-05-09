package com.knowflow.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@TableName("retrieval_record")
@EqualsAndHashCode(callSuper = true)
public class RetrievalRecordEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long qaMessageId;
    private Long documentId;
    private Long chunkId;
    private String documentName;
    private BigDecimal recallScore;
    private BigDecimal lexicalScore;
    private BigDecimal vectorScore;
    private String recallStrategy;
    private Integer rankNo;
    private String snippetText;
}
