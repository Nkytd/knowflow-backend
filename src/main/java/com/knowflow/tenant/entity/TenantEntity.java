package com.knowflow.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("tenant")
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;
    private String tenantName;
    private String status;
    private String contactName;
    private String contactPhone;
}

