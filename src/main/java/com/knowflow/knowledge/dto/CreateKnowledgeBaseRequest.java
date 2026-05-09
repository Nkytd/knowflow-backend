package com.knowflow.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    private String kbCode;

    @NotBlank(message = "知识库名称不能为空")
    private String kbName;

    private String description;
}

