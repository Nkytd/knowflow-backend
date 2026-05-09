package com.knowflow.integration.llm.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowflow.integration.llm.EmbeddingModelClient;
import com.knowflow.integration.llm.config.OpenAiChatProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class OpenAiCompatibleEmbeddingModelClient implements EmbeddingModelClient {

    private final OpenAiChatProperties properties;

    public OpenAiCompatibleEmbeddingModelClient(OpenAiChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        RestClient restClient = buildRestClient();
        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(new EmbeddingRequest(properties.getEmbeddingModel(), texts))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible embedding response did not include any vectors");
        }

        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::index))
                .map(EmbeddingItem::embedding)
                .toList();
    }

    @Override
    public String providerName() {
        return "OPENAI_COMPATIBLE";
    }

    @Override
    public String modelName() {
        return properties.getEmbeddingModel();
    }

    @Override
    public Integer dimensions() {
        return properties.getEmbeddingDimensions();
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

    private record EmbeddingRequest(
            String model,
            List<String> input
    ) {
    }

    private record EmbeddingResponse(
            List<EmbeddingItem> data
    ) {
    }

    private record EmbeddingItem(
            Integer index,
            List<Float> embedding,
            @JsonProperty("object") String objectType
    ) {
    }
}
