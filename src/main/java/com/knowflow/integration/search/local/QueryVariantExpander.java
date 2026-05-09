package com.knowflow.integration.search.local;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class QueryVariantExpander {

    private static final Pattern CJK_PATTERN = Pattern.compile(".*\\p{IsHan}.*");
    private static final List<String> CHINESE_QUESTION_PREFIXES = List.of(
            "请问", "我想知道", "帮我解释", "帮我介绍", "介绍一下", "解释一下", "说一下", "讲一下", "什么是", "什么叫", "何为"
    );
    private static final List<String> CHINESE_QUESTION_SUFFIXES = List.of(
            "是什么", "是啥", "什么意思", "的定义", "的概念", "的含义", "吗", "呢", "？", "?"
    );
    private static final List<String> CHINESE_NOISE_WORDS = List.of(
            "请问", "什么是", "什么叫", "是什么", "是啥", "什么意思", "介绍一下", "解释一下", "帮我", "一下", "这个", "那个", "相关", "内容", "资料"
    );
    private static final List<List<String>> ALIAS_GROUPS = List.of(
            List.of("knowledge base", "knowledgebase", "knowledge document", "知识库", "知识文档"),
            List.of("faq", "common question", "常见问题", "问答"),
            List.of("ticket", "work order", "service ticket", "工单", "客服单"),
            List.of("manual handoff", "human handoff", "transfer to support", "escalate", "转人工", "人工支持"),
            List.of("llm", "large model", "ai assistant", "大模型", "智能问答"),
            List.of("world model", "world models", "世界模型", "环境模型", "内部世界表征"),
            List.of("redis", "cache", "缓存"),
            List.of("rabbitmq", "message queue", "mq", "消息队列"),
            List.of("document parsing", "parse task", "parser worker", "文档解析", "解析任务"),
            List.of("vector index", "vector retrieval", "embedding", "semantic search", "向量索引", "向量检索", "向量化"),
            List.of("workbench", "portal", "console", "工作台", "控制台")
    );

    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "to", "for", "of", "on", "in", "and", "or", "with",
            "how", "what", "why", "when", "where", "which", "does", "do", "did", "can", "could", "should", "would",
            "please", "about", "into", "from", "that", "this", "these", "those", "your", "our", "me", "my"
    );

    private static final Set<String> CHINESE_STOP_WORDS = Set.of(
            "怎么", "如何", "请问", "一下", "这个", "那个", "你们", "我们", "是否", "可以", "需要", "一个", "哪些", "什么", "介绍", "解释"
    );

    List<QueryVariant> expand(String query) {
        String raw = query == null ? "" : query.trim();
        if (raw.isBlank()) {
            return List.of();
        }

        Map<String, QueryVariant> variants = new LinkedHashMap<>();
        addVariant(variants, raw, "ORIGINAL", 1.00D);

        String corePhrase = extractCorePhrase(raw);
        addVariant(variants, corePhrase, "CORE_PHRASE", 1.08D);

        String keywordVariant = buildKeywordVariant(raw, corePhrase);
        addVariant(variants, keywordVariant, "KEYWORD", 0.98D);

        String expandedVariant = buildExpandedVariant(raw, corePhrase, keywordVariant);
        addVariant(variants, expandedVariant, "EXPANDED", 0.95D);

        String bridgeVariant = buildBridgeVariant(raw, corePhrase, keywordVariant, expandedVariant);
        addVariant(variants, bridgeVariant, "BRIDGE", 0.92D);

        return variants.values().stream().limit(6).toList();
    }

    private void addVariant(Map<String, QueryVariant> variants, String text, String source, double weight) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return;
        }
        variants.putIfAbsent(normalized, new QueryVariant(text.trim(), source, weight));
    }

    private String buildKeywordVariant(String query, String corePhrase) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addUsefulTokens(keywords, splitTokens(query));
        addUsefulTokens(keywords, splitTokens(corePhrase));
        if (containsCjk(corePhrase)) {
            keywords.addAll(chineseNgrams(corePhrase, 2, 6));
        }
        return String.join(" ", keywords);
    }

    private String buildExpandedVariant(String query, String corePhrase, String keywordVariant) {
        String normalizedQuery = normalize(query + " " + corePhrase);
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        expanded.addAll(splitTokens(keywordVariant.isBlank() ? query : keywordVariant));

        for (List<String> aliasGroup : ALIAS_GROUPS) {
            boolean matched = aliasGroup.stream()
                    .map(this::normalizeCompact)
                    .anyMatch(alias -> !alias.isBlank() && normalizeCompact(normalizedQuery).contains(alias));
            if (matched) {
                expanded.addAll(aliasGroup);
            }
        }
        return String.join(" ", expanded);
    }

    private String buildBridgeVariant(String query, String corePhrase, String keywordVariant, String expandedVariant) {
        LinkedHashSet<String> bridgeTerms = new LinkedHashSet<>();
        bridgeTerms.addAll(splitTokens(query));
        bridgeTerms.addAll(splitTokens(corePhrase));
        bridgeTerms.addAll(splitTokens(keywordVariant));
        bridgeTerms.addAll(splitTokens(expandedVariant));
        if (containsCjk(corePhrase)) {
            bridgeTerms.addAll(chineseNgrams(corePhrase, 2, 5));
        }
        return String.join(" ", bridgeTerms);
    }

    private void addUsefulTokens(LinkedHashSet<String> keywords, List<String> tokens) {
        for (String token : tokens) {
            if (shouldKeepToken(token)) {
                keywords.add(token);
            }
        }
    }

    private String extractCorePhrase(String query) {
        String compact = normalizeCompact(query);
        if (compact.isBlank()) {
            return "";
        }
        for (String prefix : CHINESE_QUESTION_PREFIXES) {
            if (compact.startsWith(prefix) && compact.length() > prefix.length()) {
                compact = compact.substring(prefix.length());
                break;
            }
        }
        for (String suffix : CHINESE_QUESTION_SUFFIXES) {
            if (compact.endsWith(suffix) && compact.length() > suffix.length()) {
                compact = compact.substring(0, compact.length() - suffix.length());
                break;
            }
        }
        for (String noise : CHINESE_NOISE_WORDS) {
            compact = compact.replace(noise, "");
        }
        return compact.trim();
    }

    private List<String> chineseNgrams(String text, int min, int max) {
        String compact = normalizeCompact(text);
        if (compact.isBlank() || !containsCjk(compact)) {
            return List.of();
        }
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        int upper = Math.min(max, compact.length());
        for (int size = upper; size >= min; size--) {
            for (int start = 0; start + size <= compact.length(); start++) {
                String gram = compact.substring(start, start + size);
                if (shouldKeepToken(gram)) {
                    grams.add(gram);
                }
            }
        }
        return new ArrayList<>(grams);
    }

    private List<String> splitTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalize(text).split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean shouldKeepToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.length() <= 1) {
            return false;
        }
        if (ENGLISH_STOP_WORDS.contains(token)) {
            return false;
        }
        return !CHINESE_STOP_WORDS.contains(token);
    }

    private boolean containsCjk(String text) {
        return text != null && CJK_PATTERN.matcher(text).matches();
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

    private String normalizeCompact(String text) {
        return normalize(text).replace(" ", "");
    }

    record QueryVariant(String text, String source, double weight) {
    }
}