package com.knowflow.ticket.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.ticket.dto.UserTicketReplyRequest;
import com.knowflow.ticket.service.TicketService;
import com.knowflow.ticket.vo.TicketCommentVO;
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
@RequestMapping("/api/v1/app/tickets")
public class AppTicketController {

    private final TicketService ticketService;

    public AppTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TicketVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.success(ticketService.pageMine(pageNo, pageSize, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketVO> detail(@PathVariable Long id) {
        return ApiResponse.success(ticketService.getMineById(id));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<TicketCommentVO>> comments(@PathVariable Long id) {
        return ApiResponse.success(ticketService.listMyComments(id));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<TicketCommentVO> reply(@PathVariable Long id,
                                              @Valid @RequestBody UserTicketReplyRequest request) {
        return ApiResponse.success(ticketService.reply(id, request));
    }
}
