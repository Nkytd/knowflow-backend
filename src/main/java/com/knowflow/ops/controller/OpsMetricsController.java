package com.knowflow.ops.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.ops.service.OpsMetricsService;
import com.knowflow.ops.vo.AiUsageOverviewVO;
import com.knowflow.ops.vo.InfrastructureHealthVO;
import com.knowflow.ops.vo.TaskOpsOverviewVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ops")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class OpsMetricsController {

    private final OpsMetricsService opsMetricsService;

    public OpsMetricsController(OpsMetricsService opsMetricsService) {
        this.opsMetricsService = opsMetricsService;
    }

    @GetMapping("/tasks/overview")
    public ApiResponse<TaskOpsOverviewVO> taskOverview(@RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(opsMetricsService.taskOverview(days));
    }

    @GetMapping("/infrastructure/health")
    public ApiResponse<InfrastructureHealthVO> infrastructureHealth() {
        return ApiResponse.success(opsMetricsService.infrastructureHealth());
    }

    @GetMapping("/ai/usage")
    public ApiResponse<AiUsageOverviewVO> aiUsage(@RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(opsMetricsService.aiUsage(days));
    }
}
