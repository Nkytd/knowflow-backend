package com.knowflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("ticket_comment")
@EqualsAndHashCode(callSuper = true)
public class TicketCommentEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long ticketId;
    private String commentType;
    private Long commentUserId;
    private String content;
    private Integer visibleToUser;
}
