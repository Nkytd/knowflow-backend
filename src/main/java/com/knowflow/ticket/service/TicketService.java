package com.knowflow.ticket.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.ticket.dto.AddTicketCommentRequest;
import com.knowflow.ticket.dto.AssignTicketRequest;
import com.knowflow.ticket.dto.CloseTicketRequest;
import com.knowflow.ticket.dto.CreateQaHandoffTicketRequest;
import com.knowflow.ticket.dto.ResolveTicketRequest;
import com.knowflow.ticket.dto.UserTicketReplyRequest;
import com.knowflow.ticket.vo.TicketAssigneeOptionVO;
import com.knowflow.ticket.vo.TicketCommentVO;
import com.knowflow.ticket.vo.TicketFlowVO;
import com.knowflow.ticket.vo.TicketVO;

import java.util.List;

public interface TicketService {

    TicketVO createFromQaMessage(Long qaMessageId, CreateQaHandoffTicketRequest request);

    PageResponse<TicketVO> pageMine(Integer pageNo, Integer pageSize, String status);

    TicketVO getMineById(Long id);

    List<TicketCommentVO> listMyComments(Long id);

    TicketCommentVO reply(Long id, UserTicketReplyRequest request);

    PageResponse<TicketVO> pageAdmin(Integer pageNo, Integer pageSize, String keyword, String status,
                                     String priority, String sourceType, Long assigneeUserId, String slaStatus);

    List<TicketAssigneeOptionVO> listAssignableUsers();

    TicketVO getAdminById(Long id);

    List<TicketCommentVO> listAdminComments(Long id);

    List<TicketFlowVO> listAdminFlows(Long id);

    TicketVO accept(Long id);

    TicketVO assign(Long id, AssignTicketRequest request);

    TicketCommentVO comment(Long id, AddTicketCommentRequest request);

    TicketVO resolve(Long id, ResolveTicketRequest request);

    TicketVO close(Long id, CloseTicketRequest request);
}
