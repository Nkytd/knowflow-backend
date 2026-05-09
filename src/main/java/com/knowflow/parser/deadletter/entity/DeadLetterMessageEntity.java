package com.knowflow.parser.deadletter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dead_letter_message")
@EqualsAndHashCode(callSuper = true)
public class DeadLetterMessageEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String deadLetterNo;
    private Long taskId;
    private String taskType;
    private String taskNo;
    private Long documentId;
    private String sourceQueue;
    private String sourceExchange;
    private String routingKey;
    private String deadLetterReason;
    private String errorMessage;
    private String payloadJson;
    private Integer retryAttempt;
    private String replayStatus;
    private LocalDateTime nextRetryAt;
    private LocalDateTime replayedAt;
    private LocalDateTime resolvedAt;
    private String replayMode;
}
