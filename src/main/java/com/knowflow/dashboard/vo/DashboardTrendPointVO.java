package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DashboardTrendPointVO {

    private LocalDate statDate;
    private Long qaCount;
    private Long successCount;
    private Long noHitCount;
    private Long handoffCount;
    private Long ticketCreatedCount;
    private Long ticketResolvedCount;
    private Long draftPublishedCount;
}
