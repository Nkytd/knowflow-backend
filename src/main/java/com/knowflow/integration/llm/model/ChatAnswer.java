package com.knowflow.integration.llm.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatAnswer {

    private String content;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
}

