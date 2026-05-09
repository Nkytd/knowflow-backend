package com.knowflow.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.backflow.entity.KnowledgeDraftEntity;
import com.knowflow.backflow.mapper.KnowledgeDraftMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.entity.RetrievalRecordEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.qa.mapper.QaSessionMapper;
import com.knowflow.qa.mapper.RetrievalRecordMapper;
import com.knowflow.qa.service.AdminQaRecordService;
import com.knowflow.qa.vo.AdminQaRecordVO;
import com.knowflow.qa.vo.QueryVariantVO;
import com.knowflow.qa.vo.RetrievalDebugVO;
import com.knowflow.qa.vo.RetrievalRecordVO;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.mapper.TicketMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminQaRecordServiceImpl implements AdminQaRecordService {

    private static final String TICKET_SOURCE_QA_HANDOFF = "QA_HANDOFF";
    private static final TypeReference<List<QueryVariantVO>> QUERY_VARIANT_LIST_TYPE = new TypeReference<>() {
    };

    private final QaMessageMapper qaMessageMapper;
    private final QaSessionMapper qaSessionMapper;
    private final RetrievalRecordMapper retrievalRecordMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UserAccountMapper userAccountMapper;
    private final TicketMapper ticketMapper;
    private final KnowledgeDraftMapper knowledgeDraftMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final Double minRecallScore;

    public AdminQaRecordServiceImpl(QaMessageMapper qaMessageMapper,
                                    QaSessionMapper qaSessionMapper,
                                    RetrievalRecordMapper retrievalRecordMapper,
                                    KnowledgeBaseMapper knowledgeBaseMapper,
                                    UserAccountMapper userAccountMapper,
                                    TicketMapper ticketMapper,
                                    KnowledgeDraftMapper knowledgeDraftMapper,
                                    CurrentUserProvider currentUserProvider,
                                    ObjectMapper objectMapper,
                                    @Value("${knowflow.qa.min-recall-score:0.65}") Double minRecallScore) {
        this.qaMessageMapper = qaMessageMapper;
        this.qaSessionMapper = qaSessionMapper;
        this.retrievalRecordMapper = retrievalRecordMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.userAccountMapper = userAccountMapper;
        this.ticketMapper = ticketMapper;
        this.knowledgeDraftMapper = knowledgeDraftMapper;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.minRecallScore = minRecallScore;
    }

    @Override
    public PageResponse<AdminQaRecordVO> page(Integer pageNo,
                                              Integer pageSize,
                                              String keyword,
                                              String answerStatus,
                                              Boolean needHumanHandoff,
                                              Long knowledgeBaseId,
                                              Long sessionId) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Set<Long> sessionIds = resolveSessionIdsForFilter(tenantId, knowledgeBaseId, sessionId);
        if (knowledgeBaseId != null && sessionIds.isEmpty()) {
            return PageResponse.of(pageNo, pageSize, 0L, List.of());
        }

        Page<QaMessageEntity> page = qaMessageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .eq(knowledgeBaseId == null && sessionId != null, QaMessageEntity::getSessionId, sessionId)
                        .in(knowledgeBaseId != null, QaMessageEntity::getSessionId, sessionIds)
                        .eq(StringUtils.hasText(answerStatus), QaMessageEntity::getAnswerStatus, answerStatus)
                        .eq(needHumanHandoff != null, QaMessageEntity::getNeedHumanHandoff, Boolean.TRUE.equals(needHumanHandoff) ? 1 : 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(QaMessageEntity::getQuestionText, keyword)
                                .or()
                                .like(QaMessageEntity::getAnswerText, keyword))
                        .orderByDesc(QaMessageEntity::getCreatedAt)
        );

        return PageResponse.of((int) page.getCurrent(), (int) page.getSize(), page.getTotal(), buildRecordVOs(page.getRecords()));
    }

    @Override
    public AdminQaRecordVO getById(Long id) {
        QaMessageEntity entity = getMessageForCurrentTenant(id);
        return buildRecordVOs(List.of(entity)).get(0);
    }

    @Override
    public List<RetrievalRecordVO> listSources(Long id) {
        QaMessageEntity message = getMessageForCurrentTenant(id);
        return retrievalRecordMapper.selectList(
                        new LambdaQueryWrapper<RetrievalRecordEntity>()
                                .eq(RetrievalRecordEntity::getTenantId, message.getTenantId())
                                .eq(RetrievalRecordEntity::getQaMessageId, id)
                                .orderByAsc(RetrievalRecordEntity::getRankNo)
                ).stream()
                .map(this::toRetrievalVO)
                .toList();
    }

    private Set<Long> resolveSessionIdsForFilter(Long tenantId, Long knowledgeBaseId, Long sessionId) {
        if (knowledgeBaseId == null) {
            return Set.of();
        }
        List<QaSessionEntity> sessions = qaSessionMapper.selectList(
                new LambdaQueryWrapper<QaSessionEntity>()
                        .eq(QaSessionEntity::getTenantId, tenantId)
                        .eq(QaSessionEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(sessionId != null, QaSessionEntity::getId, sessionId)
        );
        if (sessions.isEmpty()) {
            return Set.of();
        }
        return sessions.stream()
                .map(QaSessionEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private QaMessageEntity getMessageForCurrentTenant(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        QaMessageEntity entity = qaMessageMapper.selectOne(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getId, id)
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "QA record not found");
        }
        return entity;
    }

    private List<AdminQaRecordVO> buildRecordVOs(List<QaMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        Set<Long> sessionIds = messages.stream()
                .map(QaMessageEntity::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, QaSessionEntity> sessionMap = sessionIds.isEmpty()
                ? Collections.emptyMap()
                : qaSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(QaSessionEntity::getId, Function.identity()));

        Set<Long> knowledgeBaseIds = sessionMap.values().stream()
                .map(QaSessionEntity::getKnowledgeBaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeBaseEntity> knowledgeBaseMap = knowledgeBaseIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeBaseMapper.selectBatchIds(knowledgeBaseIds).stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, Function.identity()));

        Set<Long> userIds = sessionMap.values().stream()
                .map(QaSessionEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserAccountEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, Function.identity()));

        Set<Long> messageIds = messages.stream().map(QaMessageEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, TicketEntity> ticketMap = messageIds.isEmpty()
                ? Collections.emptyMap()
                : ticketMapper.selectList(
                        new LambdaQueryWrapper<TicketEntity>()
                                .eq(TicketEntity::getSourceType, TICKET_SOURCE_QA_HANDOFF)
                                .in(TicketEntity::getSourceQaMessageId, messageIds)
                ).stream().collect(Collectors.toMap(
                        TicketEntity::getSourceQaMessageId,
                        Function.identity(),
                        (left, right) -> left
                ));

        Set<Long> ticketIds = ticketMap.values().stream()
                .map(TicketEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, KnowledgeDraftEntity> draftMap = ticketIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeDraftMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDraftEntity>()
                                .in(KnowledgeDraftEntity::getSourceTicketId, ticketIds)
                ).stream().collect(Collectors.toMap(
                        KnowledgeDraftEntity::getSourceTicketId,
                        Function.identity(),
                        (left, right) -> left
                ));

        return messages.stream()
                .map(message -> {
                    QaSessionEntity session = sessionMap.get(message.getSessionId());
                    KnowledgeBaseEntity knowledgeBase = session == null ? null : knowledgeBaseMap.get(session.getKnowledgeBaseId());
                    UserAccountEntity user = session == null ? null : userMap.get(session.getUserId());
                    TicketEntity ticket = ticketMap.get(message.getId());
                    KnowledgeDraftEntity draft = ticket == null ? null : draftMap.get(ticket.getId());
                    return AdminQaRecordVO.builder()
                            .id(message.getId())
                            .sessionId(message.getSessionId())
                            .sessionNo(session == null ? null : session.getSessionNo())
                            .sessionTitle(session == null ? null : session.getSessionTitle())
                            .knowledgeBaseId(session == null ? null : session.getKnowledgeBaseId())
                            .knowledgeBaseName(knowledgeBase == null ? null : knowledgeBase.getKbName())
                            .userId(session == null ? null : session.getUserId())
                            .username(user == null ? null : user.getUsername())
                            .realName(user == null ? null : user.getRealName())
                            .questionText(message.getQuestionText())
                            .answerText(message.getAnswerText())
                            .answerStatus(message.getAnswerStatus())
                            .modelName(message.getModelName())
                            .latencyMs(message.getLatencyMs())
                            .retrievalLatencyMs(message.getRetrievalLatencyMs())
                            .generationLatencyMs(message.getGenerationLatencyMs())
                            .retrievalCacheHit(message.getRetrievalCacheHit() != null && message.getRetrievalCacheHit() == 1)
                            .answerMode(message.getAnswerMode())
                            .sourceCount(message.getSourceCount())
                            .needHumanHandoff(message.getNeedHumanHandoff() != null && message.getNeedHumanHandoff() == 1)
                            .ticketId(ticket == null ? null : ticket.getId())
                            .ticketNo(ticket == null ? null : ticket.getTicketNo())
                            .ticketStatus(ticket == null ? null : ticket.getStatus())
                            .draftId(draft == null ? null : draft.getId())
                            .draftStatus(draft == null ? null : draft.getStatus())
                            .createdAt(message.getCreatedAt())
                            .build();
                })
                .toList();
    }


    @Override
    public RetrievalDebugVO getRetrievalDebug(Long id) {
        QaMessageEntity message = getMessageForCurrentTenant(id);
        List<RetrievalRecordVO> chunks = listSources(id);
        Double topRecallScore = chunks.stream()
                .findFirst()
                .map(RetrievalRecordVO::getRecallScore)
                .orElse(null);
        return RetrievalDebugVO.builder()
                .qaMessageId(message.getId())
                .questionText(message.getQuestionText())
                .answerStatus(message.getAnswerStatus())
                .minRecallScore(minRecallScore)
                .topRecallScore(topRecallScore)
                .queryVariants(readQueryVariants(message.getQueryVariantJson()))
                .chunks(chunks)
                .build();
    }

    private List<QueryVariantVO> readQueryVariants(String queryVariantJson) {
        if (!StringUtils.hasText(queryVariantJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(queryVariantJson, QUERY_VARIANT_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }
    private RetrievalRecordVO toRetrievalVO(RetrievalRecordEntity entity) {
        return RetrievalRecordVO.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .chunkId(entity.getChunkId())
                .documentName(entity.getDocumentName())
                .recallScore(entity.getRecallScore() == null ? null : entity.getRecallScore().doubleValue())
                .lexicalScore(entity.getLexicalScore() == null ? null : entity.getLexicalScore().doubleValue())
                .vectorScore(entity.getVectorScore() == null ? null : entity.getVectorScore().doubleValue())
                .recallStrategy(entity.getRecallStrategy())
                .rankNo(entity.getRankNo())
                .snippetText(entity.getSnippetText())
                .build();
    }
}
