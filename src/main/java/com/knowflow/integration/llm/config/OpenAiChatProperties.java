package com.knowflow.integration.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "knowflow.integration.llm.openai")
public class OpenAiChatProperties {

    private Boolean enabled = false;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String fastChatModel;
    private String thinkingChatModel;
    private String liteChatModel;
    private String experimentalChatModel;
    private String embeddingModel;
    private Integer connectTimeoutMs = 5000;
    private Integer readTimeoutMs = 30000;
    private Integer maxContextChunks = 5;
    private Integer maxContextChars = 6000;
    private Integer maxChunkChars = 1600;
    private Integer maxOutputTokens = 450;
    private Integer embeddingDimensions = 128;

    public boolean isReady() {
        return Boolean.TRUE.equals(enabled)
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(chatModel);
    }

    public boolean isEmbeddingReady() {
        return Boolean.TRUE.equals(enabled)
                && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(embeddingModel);
    }
}
