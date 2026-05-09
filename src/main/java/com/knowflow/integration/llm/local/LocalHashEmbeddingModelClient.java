package com.knowflow.integration.llm.local;

import com.knowflow.integration.llm.EmbeddingModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class LocalHashEmbeddingModelClient implements EmbeddingModelClient {

    private final int dimensions;

    public LocalHashEmbeddingModelClient(@Value("${knowflow.integration.llm.openai.embedding-dimensions:128}") Integer dimensions) {
        this.dimensions = dimensions == null || dimensions <= 0 ? 128 : dimensions;
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        List<List<Float>> embeddings = new ArrayList<>();
        if (texts == null) {
            return embeddings;
        }
        for (String text : texts) {
            embeddings.add(embedSingle(text));
        }
        return embeddings;
    }

    @Override
    public String providerName() {
        return "LOCAL_HASH";
    }

    @Override
    public String modelName() {
        return "local-hash-embedding";
    }

    @Override
    public Integer dimensions() {
        return dimensions;
    }

    private List<Float> embedSingle(String text) {
        float[] vector = new float[dimensions];
        for (String token : tokenize(normalize(text))) {
            int bucket = Math.floorMod(token.hashCode(), dimensions);
            vector[bucket] += Math.max(1, token.length() / 2.0f);
        }
        normalize(vector);
        List<Float> result = new ArrayList<>(dimensions);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        for (String token : text.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
            if (containsCjk(token) && token.length() >= 2) {
                for (int i = 0; i < token.length() - 1; i++) {
                    tokens.add(token.substring(i, i + 2));
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    private boolean containsCjk(String token) {
        return token.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private void normalize(float[] vector) {
        double sum = 0D;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0D) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
