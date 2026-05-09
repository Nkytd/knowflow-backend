package com.knowflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.entity.TicketFlowEntity;
import com.knowflow.ticket.mapper.TicketFlowMapper;
import com.knowflow.ticket.mapper.TicketMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketSlaMonitorService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_WAITING_USER = "WAITING_USER";
    private static final String SLA_BREACHED = "BREACHED";
    private static final String ACTION_SLA_BREACHED = "SLA_BREACHED";

    private final TicketMapper ticketMapper;
    private final TicketFlowMapper ticketFlowMapper;

    public TicketSlaMonitorService(TicketMapper ticketMapper, TicketFlowMapper ticketFlowMapper) {
        this.ticketMapper = ticketMapper;
        this.ticketFlowMapper = ticketFlowMapper;
    }

    @Scheduled(fixedDelayString = "${knowflow.ticket.sla-monitor-interval-ms:60000}")
    @Transactional
    public void markOverdueTickets() {
        LocalDateTime now = LocalDateTime.now();
        List<TicketEntity> overdueTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketEntity>()
                        .in(TicketEntity::getStatus, List.of(STATUS_PENDING, STATUS_PROCESSING, STATUS_WAITING_USER))
                        .ne(TicketEntity::getSlaStatus, SLA_BREACHED)
                        .isNotNull(TicketEntity::getSlaDueAt)
                        .le(TicketEntity::getSlaDueAt, now)
        );

        for (TicketEntity ticket : overdueTickets) {
            TicketEntity update = new TicketEntity();
            update.setId(ticket.getId());
            update.setSlaStatus(SLA_BREACHED);
            update.setSlaBreachedAt(now);
            update.setSlaReminderSentAt(now);
            ticketMapper.updateById(update);
            recordSlaFlow(ticket, now);
        }
    }

    private void recordSlaFlow(TicketEntity ticket, LocalDateTime now) {
        TicketFlowEntity flow = new TicketFlowEntity();
        flow.setTenantId(ticket.getTenantId());
        flow.setTicketId(ticket.getId());
        flow.setActionType(ACTION_SLA_BREACHED);
        flow.setFromStatus(ticket.getStatus());
        flow.setToStatus(ticket.getStatus());
        flow.setOperatorUserId(0L);
        flow.setRemark("SLA breached at " + now + ". The ticket requires priority follow-up.");
        ticketFlowMapper.insert(flow);
    }
}
