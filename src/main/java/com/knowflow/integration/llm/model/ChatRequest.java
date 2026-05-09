package com.knowflow.integration.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatRequest {

    private String systemPrompt;
    private String userPrompt;
    private List<String> contextChunks;
    private Double temperature;
    private String modelPreference;
}

