package com.knowflow.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("qa_message")
@EqualsAndHashCode(callSuper = true)
public class QaMessageEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long sessionId;
    private String questionText;
    private String answerText;
    private String answerStatus;
    private String queryVariantJson;
    private String modelName;
    private String promptVersion;
    private Long latencyMs;
    private Long retrievalLatencyMs;
    private Long generationLatencyMs;
    private Integer retrievalCacheHit;
    private String answerMode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer sourceCount;
    private Integer needHumanHandoff;
}

