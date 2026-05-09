package com.knowflow.integration.llm.local;

import com.knowflow.integration.llm.ChatModelClient;
import com.knowflow.integration.llm.model.ChatAnswer;
import com.knowflow.integration.llm.model.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocalTemplateChatModelClient implements ChatModelClient {

    @Override
    public ChatAnswer generate(ChatRequest request) {
        long start = System.currentTimeMillis();
        List<String> contextChunks = request.getContextChunks() == null ? List.of() : request.getContextChunks();

        StringBuilder answer = new StringBuilder();
        if (contextChunks.isEmpty()) {
            answer.append("No reliable answer was found from the current knowledge base. Please escalate to manual support.");
        } else {
            answer.append("Based on the knowledge base, the following information is relevant:\n");
            for (int i = 0; i < Math.min(3, contextChunks.size()); i++) {
                answer.append(i + 1).append(". ").append(contextChunks.get(i)).append("\n");
            }
            answer.append("If the issue still persists, please contact your support team.");
        }

        return ChatAnswer.builder()
                .content(answer.toString().trim())
                .modelName("local-template-model")
                .inputTokens(Math.max(1, request.getUserPrompt().length() / 4))
                .outputTokens(Math.max(1, answer.length() / 4))
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }
}

