package com.knowflow.qa.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.integration.search.model.KnowledgeSearchHit;
import com.knowflow.integration.search.model.QueryVariantHit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class QaRetrievalCache {

    private static final TypeReference<List<KnowledgeSearchHitCacheItem>> HIT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<QueryVariantHitCacheItem>> QUERY_VARIANT_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public QaRetrievalCache(StringRedisTemplate stringRedisTemplate,
                            ObjectMapper objectMapper,
                            @Value("${knowflow.qa.retrieval-cache-ttl-seconds:900}") Long ttlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(Math.max(30L, ttlSeconds == null ? 900L : ttlSeconds));
    }

    public Optional<List<KnowledgeSearchHit>> getHits(Long tenantId, Long knowledgeBaseId, String question, Integer topK) {
        return read(hitKey(tenantId, knowledgeBaseId, question, topK), HIT_LIST_TYPE)
                .map(items -> items.stream().map(KnowledgeSearchHitCacheItem::toHit).toList());
    }

    public void putHits(Long tenantId, Long knowledgeBaseId, String question, Integer topK, List<KnowledgeSearchHit> hits) {
        write(hitKey(tenantId, knowledgeBaseId, question, topK), hits.stream().map(KnowledgeSearchHitCacheItem::from).toList());
    }

    public Optional<List<QueryVariantHit>> getQueryVariants(String question) {
        return read(queryVariantKey(question), QUERY_VARIANT_LIST_TYPE)
                .map(items -> items.stream().map(QueryVariantHitCacheItem::toHit).toList());
    }

    public void putQueryVariants(String question, List<QueryVariantHit> variants) {
        write(queryVariantKey(question), variants.stream().map(QueryVariantHitCacheItem::from).toList());
    }

    private <T> Optional<T> read(String key, TypeReference<T> typeReference) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, typeReference));
        } catch (Exception ex) {
            log.debug("Read QA retrieval cache failed, key={}", key, ex);
            return Optional.empty();
        }
    }

    private void write(String key, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            log.debug("Serialize QA retrieval cache failed, key={}", key, ex);
        } catch (Exception ex) {
            log.debug("Write QA retrieval cache failed, key={}", key, ex);
        }
    }

    private String hitKey(Long tenantId, Long knowledgeBaseId, String question, Integer topK) {
        return "qa:retrieval:" + tenantId + ":" + knowledgeBaseId + ":" + normalizeTopK(topK) + ":" + digest(question);
    }

    private String queryVariantKey(String question) {
        return "qa:query-variants:" + digest(question);
    }

    private int normalizeTopK(Integer value) {
        return value == null || value <= 0 ? 5 : value;
    }

    private String digest(String question) {
        String normalized = question == null ? "" : question.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    @Data
    private static class KnowledgeSearchHitCacheItem {
        private Long documentId;
        private Long chunkId;
        private String documentName;
        private String snippetText;
        private Double score;
        private Double lexicalScore;
        private Double vectorScore;
        private String recallStrategy;
        private Integer rankNo;

        private static KnowledgeSearchHitCacheItem from(KnowledgeSearchHit hit) {
            KnowledgeSearchHitCacheItem item = new KnowledgeSearchHitCacheItem();
            item.setDocumentId(hit.getDocumentId());
            item.setChunkId(hit.getChunkId());
            item.setDocumentName(hit.getDocumentName());
            item.setSnippetText(hit.getSnippetText());
            item.setScore(hit.getScore());
            item.setLexicalScore(hit.getLexicalScore());
            item.setVectorScore(hit.getVectorScore());
            item.setRecallStrategy(hit.getRecallStrategy());
            item.setRankNo(hit.getRankNo());
            return item;
        }

        private KnowledgeSearchHit toHit() {
            return KnowledgeSearchHit.builder()
                    .documentId(documentId)
                    .chunkId(chunkId)
                    .documentName(documentName)
                    .snippetText(snippetText)
                    .score(score)
                    .lexicalScore(lexicalScore)
                    .vectorScore(vectorScore)
                    .recallStrategy(recallStrategy)
                    .rankNo(rankNo)
                    .build();
        }
    }

    @Data
    private static class QueryVariantHitCacheItem {
        private String text;
        private String normalizedText;
        private String source;
        private Double weight;

        private static QueryVariantHitCacheItem from(QueryVariantHit hit) {
            QueryVariantHitCacheItem item = new QueryVariantHitCacheItem();
            item.setText(hit.getText());
            item.setNormalizedText(hit.getNormalizedText());
            item.setSource(hit.getSource());
            item.setWeight(hit.getWeight());
            return item;
        }

        private QueryVariantHit toHit() {
            return QueryVariantHit.builder()
                    .text(text)
                    .normalizedText(normalizedText)
                    .source(source)
                    .weight(weight)
                    .build();
        }
    }
}