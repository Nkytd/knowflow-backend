package com.knowflow.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateKnowledgeBaseStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}

