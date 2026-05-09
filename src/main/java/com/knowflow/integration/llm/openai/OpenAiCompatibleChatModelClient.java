package com.knowflow.integration.llm.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowflow.integration.llm.ChatModelClient;
import com.knowflow.integration.llm.config.OpenAiChatProperties;
import com.knowflow.integration.llm.model.ChatAnswer;
import com.knowflow.integration.llm.model.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModelClient.class);

    private final OpenAiChatProperties properties;

    public OpenAiCompatibleChatModelClient(OpenAiChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatAnswer generate(ChatRequest request) {
        long start = System.currentTimeMillis();

        RestClient restClient = buildRestClient();
        String promptPreview = buildPromptPreview(request);
        String requestModel = resolveRequestModel(request);
        ChatCompletionRequest payload = new ChatCompletionRequest(
                requestModel,
                buildMessages(request, promptPreview),
                request.getTemperature(),
                normalizeMaxOutputTokens()
        );

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(payload)
                .retrieve()
                .body(ChatCompletionResponse.class);

        String content = extractContent(response);
        Usage usage = response == null ? null : response.usage();

        log.debug("Generated OpenAI-compatible answer with model {}", response == null ? requestModel : response.model());

        return ChatAnswer.builder()
                .content(content)
                .modelName(resolveModelName(response, requestModel))
                .inputTokens(usage == null || usage.promptTokens() == null ? estimateTokens(promptPreview) : usage.promptTokens())
                .outputTokens(usage == null || usage.completionTokens() == null ? estimateTokens(content) : usage.completionTokens())
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String resolveRequestModel(ChatRequest request) {
        String preference = request == null || request.getModelPreference() == null
                ? "FAST"
                : request.getModelPreference().trim().toUpperCase();
        return switch (preference) {
            case "THINKING" -> firstText(properties.getThinkingChatModel(), properties.getChatModel());
            case "LITE" -> firstText(properties.getLiteChatModel(), properties.getFastChatModel(), properties.getChatModel());
            case "EXPERIMENTAL" -> firstText(properties.getExperimentalChatModel(), properties.getFastChatModel(), properties.getChatModel());
            default -> firstText(properties.getFastChatModel(), properties.getChatModel());
        };
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return properties.getChatModel();
    }
    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private List<Message> buildMessages(ChatRequest request, String promptPreview) {
        List<Message> messages = new ArrayList<>();

        String systemPrompt = request.getSystemPrompt();
        if (!StringUtils.hasText(systemPrompt)) {
            systemPrompt = "你是知识库问答助手。必须使用中文回答，只能依据检索到的知识片段作答；如果知识不足，请明确说明。";
        }
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", promptPreview));
        return messages;
    }

    private String buildPromptPreview(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User question:\n")
                .append(request.getUserPrompt())
                .append("\n\nRetrieved knowledge chunks:\n");

        List<String> contextChunks = request.getContextChunks() == null ? List.of() : request.getContextChunks();
        List<String> boundedChunks = buildBoundedContextChunks(contextChunks);
        if (boundedChunks.isEmpty()) {
            prompt.append("No retrieved knowledge chunks.");
        } else {
            for (int i = 0; i < boundedChunks.size(); i++) {
                prompt.append(i + 1).append(". ").append(boundedChunks.get(i)).append("\n");
            }
        }

        prompt.append("\nPlease answer in a concise and helpful way, and do not invent facts outside the retrieved knowledge.");
        return prompt.toString().trim();
    }

    private List<String> buildBoundedContextChunks(List<String> contextChunks) {
        int maxChunks = Math.max(1, properties.getMaxContextChunks());
        int maxContextChars = Math.max(800, properties.getMaxContextChars());
        int maxChunkChars = Math.max(200, properties.getMaxChunkChars());
        List<String> boundedChunks = new ArrayList<>();
        int usedChars = 0;
        for (String chunk : contextChunks.stream().limit(maxChunks).toList()) {
            if (!StringUtils.hasText(chunk) || usedChars >= maxContextChars) {
                continue;
            }
            int remaining = maxContextChars - usedChars;
            String clipped = clipText(chunk, Math.min(maxChunkChars, remaining));
            if (StringUtils.hasText(clipped)) {
                boundedChunks.add(clipped);
                usedChars += clipped.length();
            }
        }
        return boundedChunks;
    }

    private String clipText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 20)).trim() + " ...[truncated]";
    }

    private Integer normalizeMaxOutputTokens() {
        Integer value = properties.getMaxOutputTokens();
        return value == null || value <= 0 ? null : value;
    }
    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible response did not include any choices");
        }

        Choice firstChoice = response.choices().get(0);
        if (firstChoice == null || firstChoice.message() == null || !StringUtils.hasText(firstChoice.message().content())) {
            throw new IllegalStateException("OpenAI-compatible response did not include any message content");
        }
        return firstChoice.message().content().trim();
    }

    private String resolveModelName(ChatCompletionResponse response, String requestModel) {
        if (response != null && StringUtils.hasText(response.model())) {
            return response.model();
        }
        return firstText(requestModel, properties.getFastChatModel(), properties.getChatModel());
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(
            String model,
            List<Message> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {
    }

    private record Message(
            String role,
            String content
    ) {
    }

    private record ChatCompletionResponse(
            String model,
            List<Choice> choices,
            Usage usage
    ) {
    }

    private record Choice(
            Integer index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
