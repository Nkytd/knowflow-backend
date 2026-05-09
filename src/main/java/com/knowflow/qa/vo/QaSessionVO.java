package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QaSessionVO {

    private Long id;
    private String sessionNo;
    private Long knowledgeBaseId;
    private String sessionTitle;
    private String status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

