package com.knowflow.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_log")
@EqualsAndHashCode(callSuper = true)
public class AuditLogEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String moduleCode;
    private String actionCode;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private Long operatorUserId;
    private String operatorUsername;
    private String operatorRealName;
    private String requestMethod;
    private String requestUri;
    private String operationSummary;
    private Integer successFlag;
    private String errorMessage;
}
