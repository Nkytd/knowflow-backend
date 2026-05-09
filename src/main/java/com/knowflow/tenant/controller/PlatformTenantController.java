package com.knowflow.tenant.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.tenant.dto.CreateTenantRequest;
import com.knowflow.tenant.dto.UpdateTenantRequest;
import com.knowflow.tenant.dto.UpdateTenantStatusRequest;
import com.knowflow.tenant.service.TenantService;
import com.knowflow.tenant.vo.TenantVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformTenantController {

    private final TenantService tenantService;

    public PlatformTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @OperationAudit(moduleCode = "TENANT", actionCode = "CREATE", bizType = "TENANT",
            summary = "创建租户", bizNoField = "tenantCode")
    public ApiResponse<TenantVO> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.success(tenantService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<TenantVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.success(tenantService.page(pageNo, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantVO> detail(@PathVariable Long id) {
        return ApiResponse.success(tenantService.getById(id));
    }

    @PutMapping("/{id}")
    @OperationAudit(moduleCode = "TENANT", actionCode = "UPDATE", bizType = "TENANT",
            summary = "更新租户", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<TenantVO> update(@PathVariable Long id, @Valid @RequestBody UpdateTenantRequest request) {
        return ApiResponse.success(tenantService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @OperationAudit(moduleCode = "TENANT", actionCode = "UPDATE_STATUS", bizType = "TENANT",
            summary = "更新租户状态", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTenantStatusRequest request) {
        tenantService.updateStatus(id, request.getStatus());
        return ApiResponse.success();
    }
}
