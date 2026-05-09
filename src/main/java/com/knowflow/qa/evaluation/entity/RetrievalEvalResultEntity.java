package com.knowflow.qa.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@TableName("qa_retrieval_eval_result")
@EqualsAndHashCode(callSuper = true)
public class RetrievalEvalResultEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long runId;
    private Long caseId;
    private String questionText;
    private String expectedStatus;
    private String actualStatus;
    private Long expectedDocumentId;
    private String expectedDocumentName;
    private Long actualTopDocumentId;
    private String actualTopDocumentName;
    private BigDecimal topRecallScore;
    private BigDecimal topLexicalScore;
    private BigDecimal topVectorScore;
    private Integer hitRank;
    private Integer keywordHitCount;
    private Integer keywordTotalCount;
    private Integer passed;
    private String failureReason;
    private String hitsJson;
    private String queryVariantJson;
}
