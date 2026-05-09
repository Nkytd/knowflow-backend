package com.knowflow.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class AgentToolExecuteRequest {

    @NotBlank(message = "toolCode cannot be empty")
    private String toolCode;

    private Map<String, Object> arguments;
}
