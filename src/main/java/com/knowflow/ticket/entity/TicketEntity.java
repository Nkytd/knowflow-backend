package com.knowflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
@EqualsAndHashCode(callSuper = true)
public class TicketEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String ticketNo;
    private String sourceType;
    private Long sourceQaMessageId;
    private Long reporterUserId;
    private Long assigneeUserId;
    private String title;
    private String content;
    private String priority;
    private String status;
    private String channel;
    private String slaPolicy;
    private LocalDateTime slaDueAt;
    private String slaStatus;
    private LocalDateTime slaBreachedAt;
    private LocalDateTime slaReminderSentAt;
    private LocalDateTime lastReplyAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
