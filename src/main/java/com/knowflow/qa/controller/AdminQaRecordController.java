package com.knowflow.qa.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.service.AdminQaRecordService;
import com.knowflow.qa.vo.AdminQaRecordVO;
import com.knowflow.qa.vo.RetrievalDebugVO;
import com.knowflow.qa.vo.RetrievalRecordVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/qa-records")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR','SUPPORT_AGENT')")
public class AdminQaRecordController {

    private final AdminQaRecordService adminQaRecordService;

    public AdminQaRecordController(AdminQaRecordService adminQaRecordService) {
        this.adminQaRecordService = adminQaRecordService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminQaRecordVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String answerStatus,
                                                           @RequestParam(required = false) Boolean needHumanHandoff,
                                                           @RequestParam(required = false) Long knowledgeBaseId,
                                                           @RequestParam(required = false) Long sessionId) {
        return ApiResponse.success(adminQaRecordService.page(pageNo, pageSize, keyword, answerStatus, needHumanHandoff, knowledgeBaseId, sessionId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminQaRecordVO> detail(@PathVariable Long id) {
        return ApiResponse.success(adminQaRecordService.getById(id));
    }

    @GetMapping("/{id}/sources")
    public ApiResponse<List<RetrievalRecordVO>> sources(@PathVariable Long id) {
        return ApiResponse.success(adminQaRecordService.listSources(id));
    }

    @GetMapping("/{id}/retrieval-debug")
    public ApiResponse<RetrievalDebugVO> retrievalDebug(@PathVariable Long id) {
        return ApiResponse.success(adminQaRecordService.getRetrievalDebug(id));
    }
}
