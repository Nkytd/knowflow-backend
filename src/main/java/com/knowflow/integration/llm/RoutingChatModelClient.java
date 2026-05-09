package com.knowflow.integration.llm;

import com.knowflow.integration.llm.config.OpenAiChatProperties;
import com.knowflow.integration.llm.local.LocalTemplateChatModelClient;
import com.knowflow.integration.llm.model.ChatAnswer;
import com.knowflow.integration.llm.model.ChatRequest;
import com.knowflow.integration.llm.openai.OpenAiCompatibleChatModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutingChatModelClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatModelClient.class);

    private final LocalTemplateChatModelClient localTemplateChatModelClient;
    private final OpenAiCompatibleChatModelClient openAiCompatibleChatModelClient;
    private final OpenAiChatProperties openAiChatProperties;

    public RoutingChatModelClient(LocalTemplateChatModelClient localTemplateChatModelClient,
                                  OpenAiCompatibleChatModelClient openAiCompatibleChatModelClient,
                                  OpenAiChatProperties openAiChatProperties) {
        this.localTemplateChatModelClient = localTemplateChatModelClient;
        this.openAiCompatibleChatModelClient = openAiCompatibleChatModelClient;
        this.openAiChatProperties = openAiChatProperties;
    }

    @Override
    public ChatAnswer generate(ChatRequest request) {
        if (!openAiChatProperties.isReady()) {
            return localTemplateChatModelClient.generate(request);
        }

        try {
            return openAiCompatibleChatModelClient.generate(request);
        } catch (Exception ex) {
            log.warn("OpenAI-compatible chat call failed, falling back to local template client: {}", ex.getMessage());
            return localTemplateChatModelClient.generate(request);
        }
    }
}
