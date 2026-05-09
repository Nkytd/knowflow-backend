package com.knowflow.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentToolExecutionVO {

    private String toolCode;
    private String toolName;
    private Boolean success;
    private String summary;
    private Object result;
    private LocalDateTime executedAt;
}
