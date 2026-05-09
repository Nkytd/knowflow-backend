package com.knowflow.ticket.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.ticket.dto.AddTicketCommentRequest;
import com.knowflow.ticket.dto.AssignTicketRequest;
import com.knowflow.ticket.dto.CloseTicketRequest;
import com.knowflow.ticket.dto.ResolveTicketRequest;
import com.knowflow.ticket.service.TicketService;
import com.knowflow.ticket.vo.TicketAssigneeOptionVO;
import com.knowflow.ticket.vo.TicketCommentVO;
import com.knowflow.ticket.vo.TicketFlowVO;
import com.knowflow.ticket.vo.TicketVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPPORT_AGENT')")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TicketVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String priority,
                                                    @RequestParam(required = false) String sourceType,
                                                    @RequestParam(required = false) Long assigneeUserId,
                                                    @RequestParam(required = false) String slaStatus) {
        return ApiResponse.success(ticketService.pageAdmin(pageNo, pageSize, keyword, status, priority, sourceType, assigneeUserId, slaStatus));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<TicketAssigneeOptionVO>> assignees() {
        return ApiResponse.success(ticketService.listAssignableUsers());
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketVO> detail(@PathVariable Long id) {
        return ApiResponse.success(ticketService.getAdminById(id));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<TicketCommentVO>> comments(@PathVariable Long id) {
        return ApiResponse.success(ticketService.listAdminComments(id));
    }

    @GetMapping("/{id}/flows")
    public ApiResponse<List<TicketFlowVO>> flows(@PathVariable Long id) {
        return ApiResponse.success(ticketService.listAdminFlows(id));
    }

    @PostMapping("/{id}/accept")
    @OperationAudit(moduleCode = "TICKET", actionCode = "ACCEPT", bizType = "TICKET",
            summary = "Accepted ticket for processing", bizNoField = "ticketNo")
    public ApiResponse<TicketVO> accept(@PathVariable Long id) {
        return ApiResponse.success(ticketService.accept(id));
    }

    @PostMapping("/{id}/assign")
    @OperationAudit(moduleCode = "TICKET", actionCode = "ASSIGN", bizType = "TICKET",
            summary = "Assigned ticket to a new owner", bizNoField = "ticketNo")
    public ApiResponse<TicketVO> assign(@PathVariable Long id,
                                        @Valid @RequestBody AssignTicketRequest request) {
        return ApiResponse.success(ticketService.assign(id, request));
    }

    @PostMapping("/{id}/comment")
    @OperationAudit(moduleCode = "TICKET", actionCode = "COMMENT", bizType = "TICKET",
            summary = "Added ticket handling comment", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<TicketCommentVO> comment(@PathVariable Long id,
                                                @Valid @RequestBody AddTicketCommentRequest request) {
        return ApiResponse.success(ticketService.comment(id, request));
    }

    @PostMapping("/{id}/resolve")
    @OperationAudit(moduleCode = "TICKET", actionCode = "RESOLVE", bizType = "TICKET",
            summary = "Resolved ticket", bizNoField = "ticketNo")
    public ApiResponse<TicketVO> resolve(@PathVariable Long id,
                                         @Valid @RequestBody ResolveTicketRequest request) {
        return ApiResponse.success(ticketService.resolve(id, request));
    }

    @PostMapping("/{id}/close")
    @OperationAudit(moduleCode = "TICKET", actionCode = "CLOSE", bizType = "TICKET",
            summary = "Closed ticket", bizNoField = "ticketNo")
    public ApiResponse<TicketVO> close(@PathVariable Long id,
                                       @RequestBody(required = false) CloseTicketRequest request) {
        return ApiResponse.success(ticketService.close(id, request == null ? new CloseTicketRequest() : request));
    }
}
