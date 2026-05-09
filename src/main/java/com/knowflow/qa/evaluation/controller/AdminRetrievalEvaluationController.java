package com.knowflow.qa.evaluation.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.evaluation.dto.CreateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.dto.RunRetrievalEvaluationRequest;
import com.knowflow.qa.evaluation.dto.UpdateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.service.RetrievalEvaluationService;
import com.knowflow.qa.evaluation.vo.RetrievalEvalCaseVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalResultVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalRunVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/admin/retrieval-evaluations")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class AdminRetrievalEvaluationController {

    private final RetrievalEvaluationService retrievalEvaluationService;

    public AdminRetrievalEvaluationController(RetrievalEvaluationService retrievalEvaluationService) {
        this.retrievalEvaluationService = retrievalEvaluationService;
    }

    @PostMapping("/cases")
    @OperationAudit(moduleCode = "RETRIEVAL_EVALUATION", actionCode = "CREATE_CASE", bizType = "QA_RETRIEVAL_EVAL",
            summary = "Create retrieval evaluation case", bizNoField = "caseName")
    public ApiResponse<RetrievalEvalCaseVO> createCase(@Valid @RequestBody CreateRetrievalEvalCaseRequest request) {
        return ApiResponse.success(retrievalEvaluationService.createCase(request));
    }

    @GetMapping("/cases")
    public ApiResponse<PageResponse<RetrievalEvalCaseVO>> pageCases(@RequestParam(defaultValue = "1") Integer pageNo,
                                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                                    @RequestParam(required = false) Long knowledgeBaseId,
                                                                    @RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(retrievalEvaluationService.pageCases(pageNo, pageSize, knowledgeBaseId, keyword, enabled));
    }

    @PutMapping("/cases/{id}")
    @OperationAudit(moduleCode = "RETRIEVAL_EVALUATION", actionCode = "UPDATE_CASE", bizType = "QA_RETRIEVAL_EVAL",
            summary = "Update retrieval evaluation case", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<RetrievalEvalCaseVO> updateCase(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateRetrievalEvalCaseRequest request) {
        return ApiResponse.success(retrievalEvaluationService.updateCase(id, request));
    }

    @DeleteMapping("/cases/{id}")
    @OperationAudit(moduleCode = "RETRIEVAL_EVALUATION", actionCode = "DELETE_CASE", bizType = "QA_RETRIEVAL_EVAL",
            summary = "Delete retrieval evaluation case", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> deleteCase(@PathVariable Long id) {
        retrievalEvaluationService.deleteCase(id);
        return ApiResponse.success();
    }

    @PostMapping("/runs")
    @OperationAudit(moduleCode = "RETRIEVAL_EVALUATION", actionCode = "RUN", bizType = "QA_RETRIEVAL_EVAL",
            summary = "Run retrieval evaluation", bizNoField = "runNo")
    public ApiResponse<RetrievalEvalRunVO> run(@Valid @RequestBody RunRetrievalEvaluationRequest request) {
        return ApiResponse.success(retrievalEvaluationService.run(request));
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<RetrievalEvalRunVO>> pageRuns(@RequestParam(defaultValue = "1") Integer pageNo,
                                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                                  @RequestParam(required = false) Long knowledgeBaseId) {
        return ApiResponse.success(retrievalEvaluationService.pageRuns(pageNo, pageSize, knowledgeBaseId));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<RetrievalEvalRunVO> getRun(@PathVariable Long id) {
        return ApiResponse.success(retrievalEvaluationService.getRun(id));
    }

    @GetMapping("/runs/{id}/results")
    public ApiResponse<List<RetrievalEvalResultVO>> listResults(@PathVariable Long id) {
        return ApiResponse.success(retrievalEvaluationService.listResults(id));
    }
}
