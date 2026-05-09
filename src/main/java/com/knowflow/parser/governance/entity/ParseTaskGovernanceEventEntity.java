package com.knowflow.parser.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("parse_task_governance_event")
@EqualsAndHashCode(callSuper = true)
public class ParseTaskGovernanceEventEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long taskId;
    private Long documentId;
    private String taskNo;
    private String taskType;
    private String eventType;
    private String reason;
    private String workerId;
    private LocalDateTime attemptStartedAt;
}
