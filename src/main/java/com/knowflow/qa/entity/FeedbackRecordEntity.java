package com.knowflow.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("feedback_record")
@EqualsAndHashCode(callSuper = true)
public class FeedbackRecordEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long qaMessageId;
    private Long userId;
    private String feedbackType;
    private String feedbackReason;
}

