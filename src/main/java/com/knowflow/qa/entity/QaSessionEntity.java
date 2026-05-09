package com.knowflow.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("qa_session")
@EqualsAndHashCode(callSuper = true)
public class QaSessionEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String sessionNo;
    private Long knowledgeBaseId;
    private Long userId;
    private String sessionTitle;
    private String status;
    private LocalDateTime lastMessageAt;
}

