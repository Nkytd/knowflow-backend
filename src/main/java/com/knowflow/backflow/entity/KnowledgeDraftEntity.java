package com.knowflow.backflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_draft")
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDraftEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long sourceTicketId;
    private Long knowledgeBaseId;
    private String draftType;
    private String title;
    private String questionText;
    private String answerText;
    private String status;
    private Long reviewerUserId;
    private String reviewRemark;
    private Long publishedDocumentId;
    private LocalDateTime publishedAt;
}
