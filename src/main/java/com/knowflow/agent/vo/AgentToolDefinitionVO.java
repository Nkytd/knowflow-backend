package com.knowflow.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentToolDefinitionVO {

    private String toolCode;
    private String toolName;
    private String description;
    private String requiredRole;
    private List<String> argumentHints;
}
