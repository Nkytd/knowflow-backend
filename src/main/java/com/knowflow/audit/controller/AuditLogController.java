package com.knowflow.audit.controller;

import com.knowflow.audit.service.AuditLogService;
import com.knowflow.audit.vo.AuditLogVO;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR','SUPPORT_AGENT')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLogVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String moduleCode,
                                                      @RequestParam(required = false) String actionCode,
                                                      @RequestParam(required = false) String bizType,
                                                      @RequestParam(required = false) Boolean successFlag) {
        return ApiResponse.success(auditLogService.page(pageNo, pageSize, keyword, moduleCode, actionCode, bizType, successFlag));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditLogVO> detail(@PathVariable Long id) {
        return ApiResponse.success(auditLogService.getById(id));
    }
}
