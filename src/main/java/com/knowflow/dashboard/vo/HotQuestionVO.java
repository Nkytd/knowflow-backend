package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HotQuestionVO {

    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String questionText;
    private Long askCount;
    private Long successCount;
    private Long noHitCount;
    private Long handoffCount;
    private LocalDateTime latestAskedAt;
}
