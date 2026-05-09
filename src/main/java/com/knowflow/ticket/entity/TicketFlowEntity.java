package com.knowflow.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("ticket_flow")
@EqualsAndHashCode(callSuper = true)
public class TicketFlowEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long ticketId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String remark;
}
