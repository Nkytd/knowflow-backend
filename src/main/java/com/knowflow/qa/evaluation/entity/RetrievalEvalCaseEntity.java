package com.knowflow.qa.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("qa_retrieval_eval_case")
@EqualsAndHashCode(callSuper = true)
public class RetrievalEvalCaseEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long knowledgeBaseId;
    private String caseName;
    private String questionText;
    private String expectedStatus;
    private Long expectedDocumentId;
    private String expectedDocumentName;
    private String expectedKeywords;
    private Integer topK;
    private Integer enabled;
    private String remark;
}
