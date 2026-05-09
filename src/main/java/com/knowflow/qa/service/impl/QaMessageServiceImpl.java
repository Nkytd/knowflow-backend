package com.knowflow.qa.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.integration.llm.ChatModelClient;
import com.knowflow.integration.llm.model.ChatAnswer;
import com.knowflow.integration.llm.model.ChatRequest;
import com.knowflow.integration.search.KnowledgeSearchClient;
import com.knowflow.integration.search.model.KnowledgeSearchHit;
import com.knowflow.integration.search.model.QueryVariantHit;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.knowledge.support.KnowledgeTextSanitizer;
import com.knowflow.qa.cache.QaRetrievalCache;
import com.knowflow.qa.dto.AskQuestionRequest;
import com.knowflow.qa.dto.SubmitFeedbackRequest;
import com.knowflow.qa.entity.FeedbackRecordEntity;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.entity.RetrievalRecordEntity;
import com.knowflow.qa.event.QaMessageSubmittedEvent;
import com.knowflow.qa.mapper.FeedbackRecordMapper;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.qa.mapper.QaSessionMapper;
import com.knowflow.qa.mapper.RetrievalRecordMapper;
import com.knowflow.qa.service.QaMessageService;
import com.knowflow.qa.service.QaSessionService;
import com.knowflow.qa.vo.QaMessageVO;
import com.knowflow.qa.vo.RetrievalRecordVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class QaMessageServiceImpl implements QaMessageService {

    private static final String ANSWER_STATUS_GENERATING = "GENERATING";
    private static final String ANSWER_STATUS_SUCCESS = "SUCCESS";
    private static final String ANSWER_STATUS_NO_HIT = "NO_HIT";
    private static final String ANSWER_STATUS_FAILED = "FAILED";

    private final QaMessageMapper qaMessageMapper;
    private final QaSessionMapper qaSessionMapper;
    private final RetrievalRecordMapper retrievalRecordMapper;
    private final FeedbackRecordMapper feedbackRecordMapper;
    private final QaSessionService qaSessionService;
    private final KnowledgeSearchClient knowledgeSearchClient;
    private final ChatModelClient chatModelClient;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final QaRetrievalCache qaRetrievalCache;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final Integer topK;
    private final Double minRecallScore;
    private final Double strongLexicalThreshold;
    private final Double lowConfidenceHandoffThreshold;
    private final Double fastAnswerThreshold;
    private final Double fastAnswerLexicalThreshold;

    public QaMessageServiceImpl(QaMessageMapper qaMessageMapper,
                                QaSessionMapper qaSessionMapper,
                                RetrievalRecordMapper retrievalRecordMapper,
                                FeedbackRecordMapper feedbackRecordMapper,
                                QaSessionService qaSessionService,
                                KnowledgeSearchClient knowledgeSearchClient,
                                ChatModelClient chatModelClient,
                                CurrentUserProvider currentUserProvider,
                                ApplicationEventPublisher applicationEventPublisher,
                                ObjectMapper objectMapper,
                                QaRetrievalCache qaRetrievalCache,
                                KnowledgeChunkMapper knowledgeChunkMapper,
                                @Value("${knowflow.qa.top-k:5}") Integer topK,
                                @Value("${knowflow.qa.min-recall-score:0.65}") Double minRecallScore,
                                @Value("${knowflow.qa.strong-lexical-threshold:0.82}") Double strongLexicalThreshold,
                                @Value("${knowflow.qa.low-confidence-handoff-threshold:0.50}") Double lowConfidenceHandoffThreshold,
                                @Value("${knowflow.qa.fast-answer-threshold:0.92}") Double fastAnswerThreshold,
                                @Value("${knowflow.qa.fast-answer-lexical-threshold:0.95}") Double fastAnswerLexicalThreshold) {
        this.qaMessageMapper = qaMessageMapper;
        this.qaSessionMapper = qaSessionMapper;
        this.retrievalRecordMapper = retrievalRecordMapper;
        this.feedbackRecordMapper = feedbackRecordMapper;
        this.qaSessionService = qaSessionService;
        this.knowledgeSearchClient = knowledgeSearchClient;
        this.chatModelClient = chatModelClient;
        this.currentUserProvider = currentUserProvider;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.qaRetrievalCache = qaRetrievalCache;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.topK = topK;
        this.minRecallScore = minRecallScore;
        this.strongLexicalThreshold = strongLexicalThreshold;
        this.lowConfidenceHandoffThreshold = lowConfidenceHandoffThreshold;
        this.fastAnswerThreshold = fastAnswerThreshold;
        this.fastAnswerLexicalThreshold = fastAnswerLexicalThreshold;
    }

    @Override
    @Transactional
    public QaMessageVO ask(AskQuestionRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        QaSessionEntity session = qaSessionService.getEntityByIdForCurrentUser(request.getSessionId());

        QaMessageEntity messageEntity = new QaMessageEntity();
        messageEntity.setTenantId(currentUser.tenantId());
        messageEntity.setSessionId(session.getId());
        messageEntity.setQuestionText(request.getQuestion());
        messageEntity.setAnswerText("正在检索知识库并生成回答，请稍候...");
        messageEntity.setAnswerStatus(ANSWER_STATUS_GENERATING);
        messageEntity.setModelName("KnowFlow Assistant");
        messageEntity.setLatencyMs(0L);
        messageEntity.setRetrievalLatencyMs(0L);
        messageEntity.setGenerationLatencyMs(0L);
        messageEntity.setRetrievalCacheHit(0);
        messageEntity.setAnswerMode("ASYNC_GENERATING");
        messageEntity.setInputTokens(0);
        messageEntity.setOutputTokens(0);
        messageEntity.setSourceCount(0);
        messageEntity.setNeedHumanHandoff(0);
        qaMessageMapper.insert(messageEntity);

        qaSessionService.touchLastMessageAt(session.getId());
        applicationEventPublisher.publishEvent(new QaMessageSubmittedEvent(messageEntity.getId(), request.getModelPreference()));
        return buildMessageVO(messageEntity, List.of());
    }

    @Transactional
    public void processSubmittedMessage(Long messageId, String modelPreference) {
        QaMessageEntity messageEntity = qaMessageMapper.selectById(messageId);
        if (messageEntity == null || !ANSWER_STATUS_GENERATING.equals(messageEntity.getAnswerStatus())) {
            return;
        }
        QaSessionEntity session = qaSessionMapper.selectById(messageEntity.getSessionId());
        if (session == null) {
            markMessageFailed(messageEntity, "QA session not found");
            return;
        }
        try {
            long startedAt = System.currentTimeMillis();
            CacheAwareResult<QueryVariantHit> queryVariantResult = loadQueryVariants(messageEntity.getQuestionText());
            CacheAwareResult<KnowledgeSearchHit> searchResult = loadSearchHits(
                    messageEntity.getTenantId(),
                    session.getKnowledgeBaseId(),
                    messageEntity.getQuestionText()
            );
            List<QueryVariantHit> queryVariants = queryVariantResult.items();
            List<KnowledgeSearchHit> hits = searchResult.items();
            messageEntity.setRetrievalLatencyMs(queryVariantResult.elapsedMs() + searchResult.elapsedMs());
            messageEntity.setRetrievalCacheHit(queryVariantResult.cacheHit() && searchResult.cacheHit() ? 1 : 0);

            boolean success = hasReliableHit(hits);
            List<KnowledgeSearchHit> effectiveHits = success ? hits : List.of();
            messageEntity.setQueryVariantJson(writeQueryVariants(queryVariants));

            if (success) {
                Optional<String> fastAnswer = buildFastAnswerIfConfident(effectiveHits);
                if (fastAnswer.isPresent()) {
                    messageEntity.setAnswerText(fastAnswer.get());
                    messageEntity.setAnswerStatus(ANSWER_STATUS_SUCCESS);
                    messageEntity.setModelName("fast-retrieval-answer");
                    messageEntity.setLatencyMs(System.currentTimeMillis() - startedAt);
                    messageEntity.setGenerationLatencyMs(0L);
                    messageEntity.setAnswerMode("FAST_RETRIEVAL");
                    messageEntity.setInputTokens(0);
                    messageEntity.setOutputTokens(0);
                    messageEntity.setSourceCount(effectiveHits.size());
                    messageEntity.setNeedHumanHandoff(0);
                } else {
                    ChatAnswer answer = chatModelClient.generate(ChatRequest.builder()
                            .systemPrompt(buildQaSystemPrompt())
                            .userPrompt(messageEntity.getQuestionText())
                            .contextChunks(buildExpandedContextChunks(effectiveHits))
                            .temperature(0.2)
                            .modelPreference(modelPreference)
                            .build());
                    messageEntity.setAnswerText(answer.getContent());
                    messageEntity.setAnswerStatus(ANSWER_STATUS_SUCCESS);
                    messageEntity.setModelName(answer.getModelName());
                    messageEntity.setLatencyMs(System.currentTimeMillis() - startedAt);
                    messageEntity.setGenerationLatencyMs(answer.getLatencyMs());
                    messageEntity.setAnswerMode("LLM_GENERATED");
                    messageEntity.setInputTokens(answer.getInputTokens());
                    messageEntity.setOutputTokens(answer.getOutputTokens());
                    messageEntity.setSourceCount(effectiveHits.size());
                    messageEntity.setNeedHumanHandoff(0);
                }
            } else {
                messageEntity.setAnswerText("当前知识库中没有检索到足够可靠的依据，暂时无法给出可信回答。你可以一键创建人工工单，把问题、检索结果和上下文转交支持人员继续处理。");
                messageEntity.setAnswerStatus(ANSWER_STATUS_NO_HIT);
                messageEntity.setModelName("local-retrieval-fallback");
                messageEntity.setLatencyMs(0L);
                messageEntity.setInputTokens(0);
                messageEntity.setOutputTokens(0);
                messageEntity.setSourceCount(0);
                messageEntity.setNeedHumanHandoff(1);
            }
            qaMessageMapper.updateById(messageEntity);
            saveRetrievalRecords(messageEntity, effectiveHits);
            qaSessionService.touchLastMessageAt(session.getId());
        } catch (Exception ex) {
            messageEntity.setAnswerText("问答生成失败，请稍后重试或转人工处理。失败原因：" + ex.getMessage());
            messageEntity.setAnswerStatus(ANSWER_STATUS_FAILED);
            messageEntity.setModelName("qa-async-worker");
            messageEntity.setLatencyMs(0L);
            messageEntity.setInputTokens(0);
            messageEntity.setOutputTokens(0);
            messageEntity.setSourceCount(0);
            messageEntity.setNeedHumanHandoff(1);
            qaMessageMapper.updateById(messageEntity);
        }
    }


    private void markMessageFailed(QaMessageEntity messageEntity, String reason) {
        messageEntity.setAnswerText("问答生成失败，请稍后重试或转人工处理。失败原因：" + reason);
        messageEntity.setAnswerStatus(ANSWER_STATUS_FAILED);
        messageEntity.setModelName("qa-async-worker");
        messageEntity.setLatencyMs(0L);
        messageEntity.setRetrievalLatencyMs(0L);
        messageEntity.setGenerationLatencyMs(0L);
        messageEntity.setRetrievalCacheHit(0);
        messageEntity.setAnswerMode("ASYNC_GENERATING");
        messageEntity.setInputTokens(0);
        messageEntity.setOutputTokens(0);
        messageEntity.setSourceCount(0);
        messageEntity.setNeedHumanHandoff(1);
        qaMessageMapper.updateById(messageEntity);
    }
    private void saveRetrievalRecords(QaMessageEntity messageEntity, List<KnowledgeSearchHit> effectiveHits) {
        int rankNo = 1;
        for (KnowledgeSearchHit hit : effectiveHits) {
            RetrievalRecordEntity record = new RetrievalRecordEntity();
            record.setTenantId(messageEntity.getTenantId());
            record.setQaMessageId(messageEntity.getId());
            record.setDocumentId(hit.getDocumentId());
            record.setChunkId(hit.getChunkId());
            record.setDocumentName(hit.getDocumentName());
            record.setRecallScore(BigDecimal.valueOf(hit.getScore()));
            record.setLexicalScore(hit.getLexicalScore() == null ? null : BigDecimal.valueOf(hit.getLexicalScore()));
            record.setVectorScore(hit.getVectorScore() == null ? null : BigDecimal.valueOf(hit.getVectorScore()));
            record.setRecallStrategy(hit.getRecallStrategy());
            record.setRankNo(rankNo++);
            record.setSnippetText(hit.getSnippetText());
            retrievalRecordMapper.insert(record);
        }
    }
    @Override
    public PageResponse<QaMessageVO> pageBySession(Long sessionId, Integer pageNo, Integer pageSize) {
        QaSessionEntity session = qaSessionService.getEntityByIdForCurrentUser(sessionId);
        Page<QaMessageEntity> page = qaMessageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, session.getTenantId())
                        .eq(QaMessageEntity::getSessionId, sessionId)
                        .orderByAsc(QaMessageEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream()
                        .map(entity -> buildMessageVO(entity, List.of()))
                        .toList()
        );
    }

    @Override
    public QaMessageVO getById(Long id) {
        QaMessageEntity entity = getMessageForCurrentUser(id);
        return buildMessageVO(entity, listSources(id));
    }

    @Override
    public List<RetrievalRecordVO> listSources(Long messageId) {
        QaMessageEntity message = getMessageForCurrentUser(messageId);
        return retrievalRecordMapper.selectList(
                        new LambdaQueryWrapper<RetrievalRecordEntity>()
                                .eq(RetrievalRecordEntity::getTenantId, message.getTenantId())
                                .eq(RetrievalRecordEntity::getQaMessageId, messageId)
                                .orderByAsc(RetrievalRecordEntity::getRankNo)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public void submitFeedback(Long messageId, SubmitFeedbackRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        getMessageForCurrentUser(messageId);

        FeedbackRecordEntity existing = feedbackRecordMapper.selectOne(
                new LambdaQueryWrapper<FeedbackRecordEntity>()
                        .eq(FeedbackRecordEntity::getTenantId, currentUser.tenantId())
                        .eq(FeedbackRecordEntity::getQaMessageId, messageId)
                        .eq(FeedbackRecordEntity::getUserId, currentUser.userId())
                        .last("limit 1")
        );

        if (existing == null) {
            FeedbackRecordEntity entity = new FeedbackRecordEntity();
            entity.setTenantId(currentUser.tenantId());
            entity.setQaMessageId(messageId);
            entity.setUserId(currentUser.userId());
            entity.setFeedbackType(request.getFeedbackType());
            entity.setFeedbackReason(request.getFeedbackReason());
            feedbackRecordMapper.insert(entity);
        } else {
            existing.setFeedbackType(request.getFeedbackType());
            existing.setFeedbackReason(request.getFeedbackReason());
            feedbackRecordMapper.updateById(existing);
        }
    }


    private String writeQueryVariants(List<QueryVariantHit> queryVariants) {
        try {
            return objectMapper.writeValueAsString(queryVariants == null ? List.of() : queryVariants);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
    private QaMessageEntity getMessageForCurrentUser(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        QaMessageEntity entity = qaMessageMapper.selectById(id);
        if (entity == null || !currentUser.tenantId().equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "QA message not found");
        }
        QaSessionEntity session = qaSessionService.getEntityByIdForCurrentUser(entity.getSessionId());
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "QA session not found");
        }
        return entity;
    }

    private QaMessageVO buildMessageVO(QaMessageEntity entity, List<RetrievalRecordVO> sources) {
        return QaMessageVO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .questionText(entity.getQuestionText())
                .answerText(entity.getAnswerText())
                .answerStatus(entity.getAnswerStatus())
                .modelName(entity.getModelName())
                .latencyMs(entity.getLatencyMs())
                .retrievalLatencyMs(entity.getRetrievalLatencyMs())
                .generationLatencyMs(entity.getGenerationLatencyMs())
                .retrievalCacheHit(entity.getRetrievalCacheHit() != null && entity.getRetrievalCacheHit() == 1)
                .answerMode(entity.getAnswerMode())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .sourceCount(entity.getSourceCount())
                .needHumanHandoff(entity.getNeedHumanHandoff() != null && entity.getNeedHumanHandoff() == 1)
                .createdAt(entity.getCreatedAt())
                .sources(sources)
                .build();
    }

    private CacheAwareResult<QueryVariantHit> loadQueryVariants(String question) {
        return withTiming(() -> qaRetrievalCache.getQueryVariants(question), () -> {
            List<QueryVariantHit> queryVariants = knowledgeSearchClient.explainQuery(question);
            qaRetrievalCache.putQueryVariants(question, queryVariants);
            return queryVariants;
        });
    }

    private CacheAwareResult<KnowledgeSearchHit> loadSearchHits(Long tenantId, Long knowledgeBaseId, String question) {
        return withTiming(() -> qaRetrievalCache.getHits(tenantId, knowledgeBaseId, question, topK), () -> {
            List<KnowledgeSearchHit> hits = knowledgeSearchClient.search(tenantId, knowledgeBaseId, question, topK);
            qaRetrievalCache.putHits(tenantId, knowledgeBaseId, question, topK, hits);
            return hits;
        });
    }

    private <T> CacheAwareResult<T> withTiming(Supplier<Optional<List<T>>> cacheLoader, Supplier<List<T>> originLoader) {
        long startedAt = System.currentTimeMillis();
        Optional<List<T>> cached = cacheLoader.get();
        if (cached.isPresent()) {
            return new CacheAwareResult<>(cached.get(), true, System.currentTimeMillis() - startedAt);
        }
        List<T> items = originLoader.get();
        return new CacheAwareResult<>(items, false, System.currentTimeMillis() - startedAt);
    }

    private Optional<String> buildFastAnswerIfConfident(List<KnowledgeSearchHit> hits) {
        // Do not expose raw retrieval snippets as final answers. Interview/learning questions usually need
        // synthesis, definition-first structure, and noise filtering, so even high-confidence hits should
        // go through the LLM answer composer instead of the old fast-retrieval shortcut.
        return Optional.empty();
    }

    private record CacheAwareResult<T>(List<T> items, boolean cacheHit, long elapsedMs) {
    }

    private boolean hasReliableHit(List<KnowledgeSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return false;
        }
        KnowledgeSearchHit topHit = hits.get(0);
        double topScore = defaultDouble(topHit.getScore());
        double topLexical = defaultDouble(topHit.getLexicalScore());
        if (topScore >= minRecallScore || topLexical >= strongLexicalThreshold) {
            return true;
        }
        if (topScore < lowConfidenceHandoffThreshold && topLexical < strongLexicalThreshold * 0.85D) {
            return false;
        }

        double avgTopThreeScore = hits.stream()
                .limit(3)
                .map(KnowledgeSearchHit::getScore)
                .mapToDouble(this::defaultDouble)
                .average()
                .orElse(0D);
        long strongEvidenceCount = hits.stream()
                .limit(3)
                .filter(hit -> defaultDouble(hit.getScore()) >= minRecallScore * 0.90D
                        || defaultDouble(hit.getLexicalScore()) >= strongLexicalThreshold * 0.90D)
                .count();
        return strongEvidenceCount >= 2 && (topScore * 0.65D + avgTopThreeScore * 0.35D) >= minRecallScore * 0.92D;
    }

    private String buildQaSystemPrompt() {
        return "你是 KnowFlow 知识库问答助手，必须使用中文回答。"
                + "只能依据检索到的知识片段作答，不要编造资料外事实。"
                + "回答必须先正面回答用户问题：如果用户问概念，先给定义；如果用户问原因，先给结论；如果用户问区别，先给对比。"
                + "不要把原始片段整段复制给用户，要先归纳、去重、重排，再用适合初学者的方式解释。"
                + "回答中不要输出网页 URL、页码、打印时间、站点标题、文档打印页眉页脚等噪声。"
                + "回答要结构化、直接，优先使用要点列表；如果证据确实不足，再说明缺失内容。";
    }

    private List<String> buildExpandedContextChunks(List<KnowledgeSearchHit> hits) {
        List<KnowledgeSearchHit> orderedHits = hits.stream()
                .sorted(Comparator.comparingInt(this::contextPriority))
                .toList();
        List<String> contexts = new ArrayList<>();
        Set<Long> usedChunkIds = new LinkedHashSet<>();
        for (KnowledgeSearchHit hit : orderedHits) {
            appendHitContext(contexts, usedChunkIds, hit);
            if (contexts.size() >= Math.max(topK, 6)) {
                break;
            }
        }
        return contexts;
    }

    private int contextPriority(KnowledgeSearchHit hit) {
        KnowledgeChunkEntity current = hit.getChunkId() == null ? null : knowledgeChunkMapper.selectById(hit.getChunkId());
        if (current == null || current.getChunkNo() == null) {
            return 50;
        }
        if (current.getChunkNo() <= 2 && defaultDouble(hit.getScore()) < 0.9D) {
            return 20;
        }
        return 0;
    }

    private void appendHitContext(List<String> contexts, Set<Long> usedChunkIds, KnowledgeSearchHit hit) {
        KnowledgeChunkEntity current = hit.getChunkId() == null ? null : knowledgeChunkMapper.selectById(hit.getChunkId());
        if (current == null || current.getChunkNo() == null) {
            String context = buildContextChunk(hit);
            if (!context.isBlank()) {
                contexts.add(context);
            }
            return;
        }
        List<KnowledgeChunkEntity> neighbors = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocumentId, current.getDocumentId())
                        .between(KnowledgeChunkEntity::getChunkNo, Math.max(1, current.getChunkNo() - 1), current.getChunkNo() + 2)
                        .orderByAsc(KnowledgeChunkEntity::getChunkNo)
        );
        for (KnowledgeChunkEntity chunk : neighbors) {
            if (chunk.getId() == null || usedChunkIds.contains(chunk.getId())) {
                continue;
            }
            String context = buildChunkContext(hit, chunk);
            if (context.isBlank()) {
                continue;
            }
            usedChunkIds.add(chunk.getId());
            contexts.add(context);
        }
    }

    private String buildChunkContext(KnowledgeSearchHit hit, KnowledgeChunkEntity chunk) {
        String content = KnowledgeTextSanitizer.cleanForPrompt(chunk.getContent());
        if (content.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[document] ").append(hit.getDocumentName())
                .append(" | [chunk] ").append(chunk.getChunkNo())
                .append(" | [recall_score] ").append(hit.getScore() == null ? "0" : String.format("%.3f", hit.getScore()))
                .append("\n")
                .append(content);
        return builder.toString();
    }
    private String buildContextChunk(KnowledgeSearchHit hit) {
        String snippet = KnowledgeTextSanitizer.cleanForPrompt(hit.getSnippetText());
        if (snippet.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[document] ").append(hit.getDocumentName());
        if (hit.getRankNo() != null) {
            builder.append(" | [rank] ").append(hit.getRankNo());
        }
        if (hit.getRecallStrategy() != null) {
            builder.append(" | [recall] ").append(hit.getRecallStrategy());
        }
        if (hit.getScore() != null) {
            builder.append(" | [score] ").append(String.format("%.3f", hit.getScore()));
        }
        builder.append("\n").append(snippet);
        return builder.toString();
    }

    private double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private RetrievalRecordVO toVO(RetrievalRecordEntity entity) {
        return RetrievalRecordVO.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .chunkId(entity.getChunkId())
                .documentName(entity.getDocumentName())
                .recallScore(entity.getRecallScore().doubleValue())
                .lexicalScore(entity.getLexicalScore() == null ? null : entity.getLexicalScore().doubleValue())
                .vectorScore(entity.getVectorScore() == null ? null : entity.getVectorScore().doubleValue())
                .recallStrategy(entity.getRecallStrategy())
                .rankNo(entity.getRankNo())
                .snippetText(KnowledgeTextSanitizer.cleanForPrompt(entity.getSnippetText()))
                .build();
    }
}
