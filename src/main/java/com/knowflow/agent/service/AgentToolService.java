package com.knowflow.agent.service;

import com.knowflow.agent.dto.AgentToolExecuteRequest;
import com.knowflow.agent.vo.AgentToolDefinitionVO;
import com.knowflow.agent.vo.AgentToolExecutionVO;

import java.util.List;

public interface AgentToolService {

    List<AgentToolDefinitionVO> listTools();

    AgentToolExecutionVO execute(AgentToolExecuteRequest request);
}
