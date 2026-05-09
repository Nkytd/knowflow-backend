package com.knowflow.dashboard.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.dashboard.service.DashboardService;
import com.knowflow.dashboard.vo.DashboardOverviewVO;
import com.knowflow.dashboard.vo.DashboardQuestionDetailVO;
import com.knowflow.dashboard.vo.DashboardTrendPointVO;
import com.knowflow.dashboard.vo.HotQuestionVO;
import com.knowflow.dashboard.vo.NoHitQuestionAnalysisVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR','SUPPORT_AGENT')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewVO> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/trends")
    public ApiResponse<List<DashboardTrendPointVO>> trends(@RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(dashboardService.trends(days));
    }

    @GetMapping("/hot-questions")
    public ApiResponse<List<HotQuestionVO>> hotQuestions(@RequestParam(defaultValue = "30") Integer days,
                                                         @RequestParam(defaultValue = "10") Integer limit) {
        return ApiResponse.success(dashboardService.hotQuestions(days, limit));
    }

    @GetMapping("/no-hit-questions")
    public ApiResponse<List<NoHitQuestionAnalysisVO>> noHitQuestions(@RequestParam(defaultValue = "30") Integer days,
                                                                     @RequestParam(defaultValue = "10") Integer limit) {
        return ApiResponse.success(dashboardService.noHitQuestions(days, limit));
    }

    @GetMapping("/question-detail")
    public ApiResponse<DashboardQuestionDetailVO> questionDetail(@RequestParam(defaultValue = "30") Integer days,
                                                                 @RequestParam(required = false) Long knowledgeBaseId,
                                                                 @RequestParam String questionText) {
        return ApiResponse.success(dashboardService.questionDetail(days, knowledgeBaseId, questionText));
    }
}
