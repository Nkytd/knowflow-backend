package com.knowflow.integration.search.local;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.integration.llm.EmbeddingModelClient;
import com.knowflow.integration.search.KnowledgeSearchClient;
import com.knowflow.integration.search.model.KnowledgeSearchHit;
import com.knowflow.integration.search.model.QueryVariantHit;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.entity.KnowledgeChunkIndexEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkIndexMapper;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LocalKnowledgeSearchClient implements KnowledgeSearchClient {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeSearchClient.class);
    private static final String STATUS_ENABLED = "ENABLED";
    private static final TypeReference<List<Float>> FLOAT_LIST_TYPE = new TypeReference<>() {
    };

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;
    private final EmbeddingModelClient embeddingModelClient;
    private final ObjectMapper objectMapper;
    private final QueryVariantExpander queryVariantExpander;
    private final boolean queryExpansionEnabled;
    private final int maxHitsPerDocument;
    private final double documentNameBoost;
    private final double sectionBoost;
    private final double earlyChunkBoost;
    private final double lexicalWeight;
    private final double vectorWeight;
    private final double minVectorScore;

    public LocalKnowledgeSearchClient(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                      KnowledgeChunkMapper knowledgeChunkMapper,
                                      KnowledgeChunkIndexMapper knowledgeChunkIndexMapper,
                                      EmbeddingModelClient embeddingModelClient,
                                      ObjectMapper objectMapper,
                                      @Value("${knowflow.qa.query-expansion-enabled:true}") boolean queryExpansionEnabled,
                                      @Value("${knowflow.qa.max-hits-per-document:2}") int maxHitsPerDocument,
                                      @Value("${knowflow.qa.document-name-boost:0.10}") double documentNameBoost,
                                      @Value("${knowflow.qa.section-boost:0.05}") double sectionBoost,
                                      @Value("${knowflow.qa.early-chunk-boost:0.04}") double earlyChunkBoost,
                                      @Value("${knowflow.qa.lexical-weight:0.58}") double lexicalWeight,
                                      @Value("${knowflow.qa.vector-weight:0.42}") double vectorWeight,
                                      @Value("${knowflow.qa.min-vector-score:0.12}") double minVectorScore) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
        this.embeddingModelClient = embeddingModelClient;
        this.objectMapper = objectMapper;
        this.queryVariantExpander = new QueryVariantExpander();
        this.queryExpansionEnabled = queryExpansionEnabled;
        this.maxHitsPerDocument = Math.max(1, maxHitsPerDocument);
        this.documentNameBoost = Math.max(0D, documentNameBoost);
        this.sectionBoost = Math.max(0D, sectionBoost);
        this.earlyChunkBoost = Math.max(0D, earlyChunkBoost);
        double totalWeight = Math.max(0.01D, lexicalWeight) + Math.max(0.01D, vectorWeight);
        this.lexicalWeight = Math.max(0.01D, lexicalWeight) / totalWeight;
        this.vectorWeight = Math.max(0.01D, vectorWeight) / totalWeight;
        this.minVectorScore = Math.max(0D, minVectorScore);
    }

    @Override
    public List<QueryVariantHit> explainQuery(String query) {
        return queryVariantExpander.expand(query).stream()
                .map(variant -> QueryVariantHit.builder()
                        .text(variant.text())
                        .normalizedText(normalize(variant.text()))
                        .source(variant.source())
                        .weight(variant.weight())
                        .build())
                .toList();
    }
    @Override
    public List<KnowledgeSearchHit> search(Long tenantId, Long knowledgeBaseId, String query, int topK) {
        if (tenantId == null || knowledgeBaseId == null || !StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }

        Map<Long, KnowledgeDocumentEntity> documents = loadDocuments(tenantId, knowledgeBaseId);
        if (documents.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunkEntity> chunks = loadChunks(tenantId, knowledgeBaseId, documents.keySet());
        if (chunks.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgeChunkIndexEntity> chunkIndexes = loadChunkIndexes(tenantId, knowledgeBaseId, chunks);
        List<QueryRuntimeVariant> queryVariants = prepareQueryVariants(query);
        if (queryVariants.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (KnowledgeChunkEntity chunk : chunks) {
            KnowledgeDocumentEntity document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            ScoredChunk scoredChunk = scoreChunk(
                    chunk,
                    document,
                    chunkIndexes.get(chunk.getId()),
                    queryVariants
            );
            if (scoredChunk != null) {
                scoredChunks.add(scoredChunk);
            }
        }

        if (scoredChunks.isEmpty()) {
            return List.of();
        }

        scoredChunks.sort((left, right) -> {
            int byScore = Double.compare(right.finalScore(), left.finalScore());
            if (byScore != 0) {
                return byScore;
            }
            int byLexical = Double.compare(right.lexicalScore(), left.lexicalScore());
            if (byLexical != 0) {
                return byLexical;
            }
            int byVector = Double.compare(right.vectorScore(), left.vectorScore());
            if (byVector != 0) {
                return byVector;
            }
            return Integer.compare(left.chunk().getChunkNo(), right.chunk().getChunkNo());
        });

        int safeTopK = Math.max(1, topK);
        Map<Long, Integer> hitCountByDocument = new HashMap<>();
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        for (ScoredChunk scoredChunk : scoredChunks) {
            int currentDocumentHits = hitCountByDocument.getOrDefault(scoredChunk.document().getId(), 0);
            if (currentDocumentHits >= maxHitsPerDocument) {
                continue;
            }
            hitCountByDocument.put(scoredChunk.document().getId(), currentDocumentHits + 1);
            hits.add(KnowledgeSearchHit.builder()
                    .documentId(scoredChunk.document().getId())
                    .chunkId(scoredChunk.chunk().getId())
                    .documentName(scoredChunk.document().getDocName())
                    .snippetText(buildSnippet(scoredChunk.chunk(), queryVariants))
                    .score(round(scoredChunk.finalScore()))
                    .lexicalScore(round(scoredChunk.lexicalScore()))
                    .vectorScore(round(scoredChunk.vectorScore()))
                    .recallStrategy(scoredChunk.recallStrategy())
                    .rankNo(hits.size() + 1)
                    .build());
            if (hits.size() >= safeTopK) {
                break;
            }
        }
        return hits;
    }

    private Map<Long, KnowledgeDocumentEntity> loadDocuments(Long tenantId, Long knowledgeBaseId) {
        List<KnowledgeDocumentEntity> documents = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KnowledgeDocumentEntity::getStatus, STATUS_ENABLED)
        );
        Map<Long, KnowledgeDocumentEntity> result = new HashMap<>();
        for (KnowledgeDocumentEntity document : documents) {
            result.put(document.getId(), document);
        }
        return result;
    }

    private List<KnowledgeChunkEntity> loadChunks(Long tenantId, Long knowledgeBaseId, Collection<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        return knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getTenantId, tenantId)
                        .eq(KnowledgeChunkEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KnowledgeChunkEntity::getStatus, STATUS_ENABLED)
                        .in(KnowledgeChunkEntity::getDocumentId, documentIds)
                        .orderByAsc(KnowledgeChunkEntity::getDocumentId)
                        .orderByAsc(KnowledgeChunkEntity::getChunkNo)
        );
    }

    private Map<Long, KnowledgeChunkIndexEntity> loadChunkIndexes(Long tenantId,
                                                                  Long knowledgeBaseId,
                                                                  List<KnowledgeChunkEntity> chunks) {
        if (chunks.isEmpty()) {
            return Map.of();
        }
        List<Long> chunkIds = chunks.stream().map(KnowledgeChunkEntity::getId).toList();
        List<KnowledgeChunkIndexEntity> indexes = knowledgeChunkIndexMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkIndexEntity>()
                        .eq(KnowledgeChunkIndexEntity::getTenantId, tenantId)
                        .eq(KnowledgeChunkIndexEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KnowledgeChunkIndexEntity::getStatus, STATUS_ENABLED)
                        .in(KnowledgeChunkIndexEntity::getChunkId, chunkIds)
        );
        Map<Long, KnowledgeChunkIndexEntity> result = new HashMap<>();
        for (KnowledgeChunkIndexEntity index : indexes) {
            result.put(index.getChunkId(), index);
        }
        return result;
    }

    private List<QueryRuntimeVariant> prepareQueryVariants(String query) {
        List<QueryVariantExpander.QueryVariant> variants;
        if (queryExpansionEnabled) {
            variants = queryVariantExpander.expand(query);
        } else {
            variants = List.of(new QueryVariantExpander.QueryVariant(query, "ORIGINAL", 1.0D));
        }

        if (variants.isEmpty()) {
            return List.of();
        }

        List<List<Float>> embeddings = embeddingModelClient.embed(variants.stream().map(QueryVariantExpander.QueryVariant::text).toList());
        List<QueryRuntimeVariant> runtimeVariants = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            QueryVariantExpander.QueryVariant variant = variants.get(i);
            List<String> tokens = buildSearchTokens(variant.text());
            if (tokens.isEmpty()) {
                continue;
            }
            List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : List.of();
            runtimeVariants.add(new QueryRuntimeVariant(
                    variant,
                    normalize(variant.text()),
                    tokens,
                    embedding,
                    vectorNorm(embedding)
            ));
        }
        return runtimeVariants;
    }

    private ScoredChunk scoreChunk(KnowledgeChunkEntity chunk,
                                   KnowledgeDocumentEntity document,
                                   KnowledgeChunkIndexEntity chunkIndex,
                                   List<QueryRuntimeVariant> queryVariants) {
        String chunkText = defaultString(chunk.getContent());
        String section = defaultString(chunk.getSourceSection());
        String documentName = defaultString(document.getDocName());
        String searchableText = normalize(documentName + " " + section + " " + chunkText);
        if (searchableText.isBlank()) {
            return null;
        }

        List<Float> chunkEmbedding = readEmbedding(chunkIndex);
        double chunkVectorNorm = readVectorNorm(chunkIndex, chunkEmbedding);

        double bestScore = 0D;
        double bestLexical = 0D;
        double bestVector = 0D;
        String bestStrategy = "LEXICAL_ORIGINAL";
        int evidenceCount = 0;

        for (QueryRuntimeVariant queryVariant : queryVariants) {
            double lexicalScore = lexicalScore(searchableText, queryVariant);
            double vectorScore = vectorScore(queryVariant.embedding(), queryVariant.vectorNorm(), chunkEmbedding, chunkVectorNorm);
            double blendedScore = blendScore(lexicalScore, vectorScore, queryVariant.variant().weight());
            if (lexicalScore >= 0.18D || vectorScore >= minVectorScore || blendedScore >= 0.22D) {
                evidenceCount++;
            }
            if (blendedScore > bestScore) {
                bestScore = blendedScore;
                bestLexical = lexicalScore;
                bestVector = vectorScore;
                bestStrategy = detectRecallStrategy(lexicalScore, vectorScore, queryVariant.variant().source());
            }
        }

        if (bestScore < 0.12D && bestLexical < 0.18D && bestVector < minVectorScore) {
            return null;
        }

        double finalScore = bestScore
                + documentNameBoost(documentName, queryVariants)
                + sectionBoost(section, queryVariants)
                + earlyChunkBoost(chunk.getChunkNo())
                + consensusBoost(evidenceCount);

        return new ScoredChunk(chunk, document, bestStrategy, bestLexical, bestVector, finalScore);
    }

    private double lexicalScore(String searchableText, QueryRuntimeVariant queryVariant) {
        Set<String> uniqueTokens = new LinkedHashSet<>(queryVariant.tokens());
        if (uniqueTokens.isEmpty()) {
            return 0D;
        }

        double totalWeight = 0D;
        double matchedWeight = 0D;
        int matchedTokenCount = 0;
        for (String token : uniqueTokens) {
            double weight = tokenWeight(token);
            totalWeight += weight;
            if (searchableText.contains(token)) {
                matchedWeight += weight;
                matchedTokenCount++;
            }
        }

        if (totalWeight == 0D) {
            return 0D;
        }

        double coverage = matchedWeight / totalWeight;
        double phraseBonus = searchableText.contains(queryVariant.normalizedText()) ? 0.18D : 0D;
        double majorityBonus = coverage >= 0.75D ? 0.08D : 0D;
        double denseMatchBonus = matchedTokenCount >= 3 ? 0.14D : 0D;
        double focusedMatchBonus = matchedTokenCount >= 2 && coverage >= 0.50D ? 0.08D : 0D;
        return Math.min(1D, coverage + phraseBonus + majorityBonus + denseMatchBonus + focusedMatchBonus);
    }

    private double blendScore(double lexicalScore, double vectorScore, double variantWeight) {
        double blendedScore;
        if (lexicalScore > 0D && vectorScore > 0D) {
            blendedScore = lexicalScore * lexicalWeight + vectorScore * vectorWeight;
        } else if (lexicalScore > 0D) {
            blendedScore = lexicalScore * 0.92D;
        } else {
            blendedScore = vectorScore * 0.85D;
        }
        return blendedScore * variantWeight;
    }

    private String detectRecallStrategy(double lexicalScore, double vectorScore, String source) {
        String mode;
        if (lexicalScore >= 0.25D && vectorScore >= 0.25D) {
            mode = "HYBRID";
        } else if (lexicalScore >= vectorScore) {
            mode = "LEXICAL";
        } else {
            mode = "VECTOR";
        }
        return mode + "_" + source;
    }

    private double documentNameBoost(String documentName, List<QueryRuntimeVariant> queryVariants) {
        if (!StringUtils.hasText(documentName)) {
            return 0D;
        }
        String normalizedName = normalize(documentName);
        double bestCoverage = 0D;
        for (QueryRuntimeVariant queryVariant : queryVariants) {
            bestCoverage = Math.max(bestCoverage, fieldCoverage(normalizedName, queryVariant.tokens()));
        }
        return documentNameBoost * bestCoverage;
    }

    private double sectionBoost(String section, List<QueryRuntimeVariant> queryVariants) {
        if (!StringUtils.hasText(section)) {
            return 0D;
        }
        String normalizedSection = normalize(section);
        double bestCoverage = 0D;
        for (QueryRuntimeVariant queryVariant : queryVariants) {
            bestCoverage = Math.max(bestCoverage, fieldCoverage(normalizedSection, queryVariant.tokens()));
        }
        return sectionBoost * bestCoverage;
    }

    private double fieldCoverage(String normalizedText, List<String> queryTokens) {
        if (normalizedText.isBlank() || queryTokens.isEmpty()) {
            return 0D;
        }
        Set<String> uniqueTokens = new LinkedHashSet<>(queryTokens);
        int matched = 0;
        for (String token : uniqueTokens) {
            if (normalizedText.contains(token)) {
                matched++;
            }
        }
        return (double) matched / uniqueTokens.size();
    }

    private double earlyChunkBoost(Integer chunkNo) {
        if (chunkNo == null || chunkNo <= 0 || chunkNo > 3) {
            return 0D;
        }
        double rankFactor = switch (chunkNo) {
            case 1 -> 1D;
            case 2 -> 0.7D;
            default -> 0.4D;
        };
        return earlyChunkBoost * rankFactor;
    }

    private double consensusBoost(int evidenceCount) {
        if (evidenceCount <= 1) {
            return 0D;
        }
        return Math.min(0.08D, 0.03D * (evidenceCount - 1));
    }

    private String buildSnippet(KnowledgeChunkEntity chunk, List<QueryRuntimeVariant> queryVariants) {
        String content = defaultString(chunk.getContent()).trim();
        if (content.isBlank()) {
            return "";
        }
        String loweredContent = content.toLowerCase(Locale.ROOT);
        String bestToken = "";
        for (QueryRuntimeVariant queryVariant : queryVariants) {
            for (String token : queryVariant.tokens()) {
                if (token.length() <= bestToken.length()) {
                    continue;
                }
                if (loweredContent.contains(token.toLowerCase(Locale.ROOT))) {
                    bestToken = token;
                }
            }
        }

        int maxLength = 220;
        if (content.length() <= maxLength) {
            return content;
        }
        if (bestToken.isBlank()) {
            return content.substring(0, maxLength) + "...";
        }

        int hitIndex = loweredContent.indexOf(bestToken.toLowerCase(Locale.ROOT));
        if (hitIndex < 0) {
            return content.substring(0, maxLength) + "...";
        }

        int start = Math.max(0, hitIndex - 70);
        int end = Math.min(content.length(), start + maxLength);
        if (end - start < maxLength && start > 0) {
            start = Math.max(0, end - maxLength);
        }
        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + content.substring(start, end).trim() + suffix;
    }

    private List<Float> readEmbedding(KnowledgeChunkIndexEntity chunkIndex) {
        if (chunkIndex == null || !StringUtils.hasText(chunkIndex.getEmbeddingJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(chunkIndex.getEmbeddingJson(), FLOAT_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to parse chunk embedding. chunkId={}, error={}", chunkIndex.getChunkId(), ex.getMessage());
            return List.of();
        }
    }

    private double readVectorNorm(KnowledgeChunkIndexEntity chunkIndex, List<Float> embedding) {
        if (chunkIndex != null && chunkIndex.getVectorNorm() != null) {
            return chunkIndex.getVectorNorm().doubleValue();
        }
        return vectorNorm(embedding);
    }

    private double vectorScore(List<Float> queryEmbedding,
                               double queryVectorNorm,
                               List<Float> chunkEmbedding,
                               double chunkVectorNorm) {
        if (queryEmbedding == null || chunkEmbedding == null || queryEmbedding.isEmpty() || chunkEmbedding.isEmpty()) {
            return 0D;
        }
        if (queryVectorNorm <= 0D || chunkVectorNorm <= 0D) {
            return 0D;
        }

        int size = Math.min(queryEmbedding.size(), chunkEmbedding.size());
        double dot = 0D;
        for (int i = 0; i < size; i++) {
            Float left = queryEmbedding.get(i);
            Float right = chunkEmbedding.get(i);
            if (left == null || right == null) {
                continue;
            }
            dot += left * right;
        }
        return Math.max(0D, dot / (queryVectorNorm * chunkVectorNorm));
    }

    private double vectorNorm(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return 0D;
        }
        double sum = 0D;
        for (Float value : embedding) {
            if (value == null) {
                continue;
            }
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private List<String> buildSearchTokens(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
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

    private double tokenWeight(String token) {
        return Math.min(2.2D, 0.8D + token.length() * 0.12D);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String defaultString(String text) {
        return text == null ? "" : text;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private record QueryRuntimeVariant(QueryVariantExpander.QueryVariant variant,
                                       String normalizedText,
                                       List<String> tokens,
                                       List<Float> embedding,
                                       double vectorNorm) {
    }

    private record ScoredChunk(KnowledgeChunkEntity chunk,
                               KnowledgeDocumentEntity document,
                               String recallStrategy,
                               double lexicalScore,
                               double vectorScore,
                               double finalScore) {
    }
}
