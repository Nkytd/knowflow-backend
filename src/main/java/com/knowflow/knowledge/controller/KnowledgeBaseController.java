package com.knowflow.knowledge.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.knowledge.dto.CreateKnowledgeBaseRequest;
import com.knowflow.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.knowflow.knowledge.dto.UpdateKnowledgeBaseStatusRequest;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.knowledge.vo.KnowledgeBaseVO;
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
@RequestMapping("/api/v1/admin/knowledge-bases")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    @OperationAudit(moduleCode = "KNOWLEDGE_BASE", actionCode = "CREATE", bizType = "KNOWLEDGE_BASE",
            summary = "创建知识库", bizNoField = "kbCode")
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeBaseVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status) {
        return ApiResponse.success(knowledgeBaseService.page(pageNo, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.getById(id));
    }

    @PutMapping("/{id}")
    @OperationAudit(moduleCode = "KNOWLEDGE_BASE", actionCode = "UPDATE", bizType = "KNOWLEDGE_BASE",
            summary = "更新知识库", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @OperationAudit(moduleCode = "KNOWLEDGE_BASE", actionCode = "UPDATE_STATUS", bizType = "KNOWLEDGE_BASE",
            summary = "切换知识库状态", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateKnowledgeBaseStatusRequest request) {
        knowledgeBaseService.updateStatus(id, request.getStatus());
        return ApiResponse.success();
    }
}
