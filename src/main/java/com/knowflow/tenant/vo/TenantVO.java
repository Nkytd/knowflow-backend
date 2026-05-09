package com.knowflow.tenant.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantVO {

    private Long id;
    private String tenantCode;
    private String tenantName;
    private String status;
    private String contactName;
    private String contactPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

