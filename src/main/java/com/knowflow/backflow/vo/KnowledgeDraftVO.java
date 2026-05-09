package com.knowflow.backflow.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDraftVO {

    private Long id;
    private Long sourceTicketId;
    private String sourceTicketNo;
    private String sourceTicketTitle;
    private Long sourceQaMessageId;
    private String sourceQuestionText;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String draftType;
    private String title;
    private String questionText;
    private String answerText;
    private String status;
    private Long reviewerUserId;
    private String reviewerName;
    private String reviewRemark;
    private Long publishedDocumentId;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
