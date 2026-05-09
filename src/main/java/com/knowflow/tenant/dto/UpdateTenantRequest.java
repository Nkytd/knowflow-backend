package com.knowflow.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTenantRequest {

    @NotBlank(message = "租户名称不能为空")
    private String tenantName;

    private String contactName;
    private String contactPhone;
}

