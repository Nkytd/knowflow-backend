package com.knowflow.integration.llm;

import java.util.List;

public interface EmbeddingModelClient {

    List<List<Float>> embed(List<String> texts);

    default String providerName() {
        return "LOCAL";
    }

    default String modelName() {
        return "local-hash-embedding";
    }

    default Integer dimensions() {
        return null;
    }
}
