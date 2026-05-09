package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardOverviewVO {

    private Long knowledgeBaseCount;
    private Long documentCount;
    private Long qaCount;
    private Long qaSuccessCount;
    private Long qaNoHitCount;
    private Double qaHitRate;
    private Long handoffCount;
    private Double handoffRate;
    private Long ticketCount;
    private Long openTicketCount;
    private Long resolvedTicketCount;
    private Double ticketResolveRate;
    private Long draftPendingReviewCount;
    private Long draftPublishedCount;
}
