package com.knowflow.qa.controller;

import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.dto.AskQuestionRequest;
import com.knowflow.qa.dto.SubmitFeedbackRequest;
import com.knowflow.qa.service.QaMessageService;
import com.knowflow.qa.vo.QaMessageVO;
import com.knowflow.qa.vo.RetrievalRecordVO;
import com.knowflow.ticket.dto.CreateQaHandoffTicketRequest;
import com.knowflow.ticket.service.TicketService;
import com.knowflow.ticket.vo.TicketVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/app/qa/messages")
public class QaMessageController {

    private final QaMessageService qaMessageService;
    private final TicketService ticketService;

    public QaMessageController(QaMessageService qaMessageService, TicketService ticketService) {
        this.qaMessageService = qaMessageService;
        this.ticketService = ticketService;
    }

    @PostMapping
    public ApiResponse<QaMessageVO> ask(@Valid @RequestBody AskQuestionRequest request) {
        return ApiResponse.success(qaMessageService.ask(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<QaMessageVO>> page(@RequestParam Long sessionId,
                                                       @RequestParam(defaultValue = "1") Integer pageNo,
                                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(qaMessageService.pageBySession(sessionId, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<QaMessageVO> detail(@PathVariable Long id) {
        return ApiResponse.success(qaMessageService.getById(id));
    }

    @GetMapping("/{id}/sources")
    public ApiResponse<List<RetrievalRecordVO>> sources(@PathVariable Long id) {
        return ApiResponse.success(qaMessageService.listSources(id));
    }

    @PostMapping("/{id}/feedback")
    public ApiResponse<Void> feedback(@PathVariable Long id, @Valid @RequestBody SubmitFeedbackRequest request) {
        qaMessageService.submitFeedback(id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/handoff")
    @OperationAudit(moduleCode = "QA", actionCode = "HANDOFF_TO_TICKET", bizType = "TICKET",
            summary = "Converted QA record into support ticket", bizNoField = "ticketNo")
    public ApiResponse<TicketVO> handoff(@PathVariable Long id,
                                         @RequestBody(required = false) CreateQaHandoffTicketRequest request) {
        return ApiResponse.success(ticketService.createFromQaMessage(id, request));
    }
}
