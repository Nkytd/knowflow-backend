package com.knowflow.parser.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.parser.service.ParseTaskService;
import com.knowflow.parser.vo.ParseTaskVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/parse-tasks")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class ParseTaskController {

    private final ParseTaskService parseTaskService;

    public ParseTaskController(ParseTaskService parseTaskService) {
        this.parseTaskService = parseTaskService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ParseTaskVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) Long documentId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String taskType) {
        return ApiResponse.success(parseTaskService.page(pageNo, pageSize, documentId, status, taskType));
    }

    @GetMapping("/{id}")
    public ApiResponse<ParseTaskVO> detail(@PathVariable Long id) {
        return ApiResponse.success(parseTaskService.getById(id));
    }

    @PostMapping("/{id}/retry")
    @OperationAudit(moduleCode = "PARSE_TASK", actionCode = "RETRY", bizType = "PARSE_TASK",
            summary = "重试解析或索引任务", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> retry(@PathVariable Long id) {
        parseTaskService.retry(id);
        return ApiResponse.success();
    }
}
