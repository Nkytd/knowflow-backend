package com.knowflow.qa.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qa_retrieval_eval_run")
@EqualsAndHashCode(callSuper = true)
public class RetrievalEvalRunEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String runNo;
    private Long knowledgeBaseId;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private BigDecimal passRate;
    private BigDecimal recallAtK;
    private BigDecimal top1HitRate;
    private BigDecimal noHitAccuracy;
    private BigDecimal avgTopScore;
    private BigDecimal avgTopLexicalScore;
    private BigDecimal avgTopVectorScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
