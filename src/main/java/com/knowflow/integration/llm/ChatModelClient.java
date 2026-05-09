package com.knowflow.integration.llm;

import com.knowflow.integration.llm.model.ChatAnswer;
import com.knowflow.integration.llm.model.ChatRequest;

public interface ChatModelClient {

    ChatAnswer generate(ChatRequest request);
}

