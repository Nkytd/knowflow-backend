package com.knowflow.integration.llm;

import com.knowflow.integration.llm.config.OpenAiChatProperties;
import com.knowflow.integration.llm.local.LocalHashEmbeddingModelClient;
import com.knowflow.integration.llm.openai.OpenAiCompatibleEmbeddingModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class RoutingEmbeddingModelClient implements EmbeddingModelClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingEmbeddingModelClient.class);

    private final LocalHashEmbeddingModelClient localHashEmbeddingModelClient;
    private final OpenAiCompatibleEmbeddingModelClient openAiCompatibleEmbeddingModelClient;
    private final OpenAiChatProperties openAiChatProperties;

    public RoutingEmbeddingModelClient(LocalHashEmbeddingModelClient localHashEmbeddingModelClient,
                                       OpenAiCompatibleEmbeddingModelClient openAiCompatibleEmbeddingModelClient,
                                       OpenAiChatProperties openAiChatProperties) {
        this.localHashEmbeddingModelClient = localHashEmbeddingModelClient;
        this.openAiCompatibleEmbeddingModelClient = openAiCompatibleEmbeddingModelClient;
        this.openAiChatProperties = openAiChatProperties;
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        if (!openAiChatProperties.isEmbeddingReady()) {
            return localHashEmbeddingModelClient.embed(texts);
        }
        try {
            return openAiCompatibleEmbeddingModelClient.embed(texts);
        } catch (Exception ex) {
            log.warn("OpenAI-compatible embedding call failed, falling back to local hash embedding: {}", ex.getMessage());
            return localHashEmbeddingModelClient.embed(texts);
        }
    }

    @Override
    public String providerName() {
        if (openAiChatProperties.isEmbeddingReady()) {
            return openAiCompatibleEmbeddingModelClient.providerName();
        }
        return localHashEmbeddingModelClient.providerName();
    }

    @Override
    public String modelName() {
        if (openAiChatProperties.isEmbeddingReady()) {
            return openAiCompatibleEmbeddingModelClient.modelName();
        }
        return localHashEmbeddingModelClient.modelName();
    }

    @Override
    public Integer dimensions() {
        if (openAiChatProperties.isEmbeddingReady()) {
            return openAiCompatibleEmbeddingModelClient.dimensions();
        }
        return localHashEmbeddingModelClient.dimensions();
    }
}
