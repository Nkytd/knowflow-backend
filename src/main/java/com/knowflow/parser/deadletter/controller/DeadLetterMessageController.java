package com.knowflow.parser.deadletter.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.deadletter.vo.DeadLetterMessageVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dead-letters")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class DeadLetterMessageController {

    private final DeadLetterMessageService deadLetterMessageService;

    public DeadLetterMessageController(DeadLetterMessageService deadLetterMessageService) {
        this.deadLetterMessageService = deadLetterMessageService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DeadLetterMessageVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                                               @RequestParam(required = false) String replayStatus,
                                                               @RequestParam(required = false) String taskType,
                                                               @RequestParam(required = false) Long taskId,
                                                               @RequestParam(required = false) Long documentId,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(deadLetterMessageService.page(pageNo, pageSize, replayStatus, taskType, taskId, documentId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeadLetterMessageVO> detail(@PathVariable Long id) {
        return ApiResponse.success(deadLetterMessageService.getById(id));
    }

    @PostMapping("/{id}/replay")
    @OperationAudit(moduleCode = "DEAD_LETTER", actionCode = "REPLAY", bizType = "PARSE_TASK",
            summary = "人工回放死信任务", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> replay(@PathVariable Long id) {
        deadLetterMessageService.replay(id);
        return ApiResponse.success();
    }
}
