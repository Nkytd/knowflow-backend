package com.knowflow.agent.controller;

import com.knowflow.agent.dto.AgentToolExecuteRequest;
import com.knowflow.agent.service.AgentToolService;
import com.knowflow.agent.vo.AgentToolDefinitionVO;
import com.knowflow.agent.vo.AgentToolExecutionVO;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/app/agent/tools")
public class AgentToolController {

    private final AgentToolService agentToolService;

    public AgentToolController(AgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @GetMapping
    public ApiResponse<List<AgentToolDefinitionVO>> listTools() {
        return ApiResponse.success(agentToolService.listTools());
    }

    @PostMapping("/execute")
    @OperationAudit(moduleCode = "AGENT", actionCode = "EXECUTE_TOOL", bizType = "AGENT_TOOL",
            summary = "Executed agent tool")
    public ApiResponse<AgentToolExecutionVO> execute(@Valid @RequestBody AgentToolExecuteRequest request) {
        return ApiResponse.success(agentToolService.execute(request));
    }
}
