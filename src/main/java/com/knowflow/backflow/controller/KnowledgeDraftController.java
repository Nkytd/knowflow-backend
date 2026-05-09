package com.knowflow.backflow.controller;

import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.backflow.dto.CreateKnowledgeDraftFromTicketRequest;
import com.knowflow.backflow.dto.ReviewKnowledgeDraftRequest;
import com.knowflow.backflow.dto.UpdateKnowledgeDraftRequest;
import com.knowflow.backflow.service.KnowledgeDraftService;
import com.knowflow.backflow.vo.KnowledgeBaseOptionVO;
import com.knowflow.backflow.vo.KnowledgeDraftVO;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/knowledge-drafts")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR','SUPPORT_AGENT')")
public class KnowledgeDraftController {

    private final KnowledgeDraftService knowledgeDraftService;

    public KnowledgeDraftController(KnowledgeDraftService knowledgeDraftService) {
        this.knowledgeDraftService = knowledgeDraftService;
    }

    @PostMapping("/from-ticket/{ticketId}")
    @OperationAudit(moduleCode = "KNOWLEDGE_DRAFT", actionCode = "CREATE_FROM_TICKET", bizType = "KNOWLEDGE_DRAFT",
            summary = "Generated knowledge draft from ticket", bizNoField = "title")
    public ApiResponse<KnowledgeDraftVO> createFromTicket(@PathVariable Long ticketId,
                                                          @Valid @RequestBody CreateKnowledgeDraftFromTicketRequest request) {
        return ApiResponse.success(knowledgeDraftService.createFromTicket(ticketId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeDraftVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                                            @RequestParam(required = false) Long knowledgeBaseId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String draftType) {
        return ApiResponse.success(knowledgeDraftService.page(pageNo, pageSize, knowledgeBaseId, status, draftType));
    }

    @GetMapping("/knowledge-bases/options")
    public ApiResponse<List<KnowledgeBaseOptionVO>> knowledgeBaseOptions() {
        return ApiResponse.success(knowledgeDraftService.listKnowledgeBaseOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeDraftVO> detail(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDraftService.getById(id));
    }

    @PutMapping("/{id}")
    @OperationAudit(moduleCode = "KNOWLEDGE_DRAFT", actionCode = "UPDATE", bizType = "KNOWLEDGE_DRAFT",
            summary = "Updated knowledge draft content", bizNoField = "title")
    public ApiResponse<KnowledgeDraftVO> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateKnowledgeDraftRequest request) {
        return ApiResponse.success(knowledgeDraftService.update(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
    @OperationAudit(moduleCode = "KNOWLEDGE_DRAFT", actionCode = "APPROVE", bizType = "KNOWLEDGE_DRAFT",
            summary = "Approved knowledge draft", bizNoField = "title")
    public ApiResponse<KnowledgeDraftVO> approve(@PathVariable Long id,
                                                 @RequestBody(required = false) ReviewKnowledgeDraftRequest request) {
        return ApiResponse.success(knowledgeDraftService.approve(id, request == null ? new ReviewKnowledgeDraftRequest() : request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
    @OperationAudit(moduleCode = "KNOWLEDGE_DRAFT", actionCode = "REJECT", bizType = "KNOWLEDGE_DRAFT",
            summary = "Rejected knowledge draft", bizNoField = "title")
    public ApiResponse<KnowledgeDraftVO> reject(@PathVariable Long id,
                                                @RequestBody(required = false) ReviewKnowledgeDraftRequest request) {
        return ApiResponse.success(knowledgeDraftService.reject(id, request == null ? new ReviewKnowledgeDraftRequest() : request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
    @OperationAudit(moduleCode = "KNOWLEDGE_DRAFT", actionCode = "PUBLISH", bizType = "KNOWLEDGE_DRAFT",
            summary = "Published knowledge draft into knowledge base", bizNoField = "title")
    public ApiResponse<KnowledgeDraftVO> publish(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDraftService.publish(id));
    }
}
