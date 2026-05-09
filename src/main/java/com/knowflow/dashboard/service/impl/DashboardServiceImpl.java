package com.knowflow.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.backflow.entity.KnowledgeDraftEntity;
import com.knowflow.backflow.mapper.KnowledgeDraftMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.dashboard.service.DashboardService;
import com.knowflow.dashboard.vo.DashboardOverviewVO;
import com.knowflow.dashboard.vo.DashboardQuestionDetailVO;
import com.knowflow.dashboard.vo.DashboardQuestionDraftVO;
import com.knowflow.dashboard.vo.DashboardQuestionMessageVO;
import com.knowflow.dashboard.vo.DashboardQuestionTicketVO;
import com.knowflow.dashboard.vo.DashboardTrendPointVO;
import com.knowflow.dashboard.vo.HotQuestionVO;
import com.knowflow.dashboard.vo.NoHitQuestionAnalysisVO;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.qa.mapper.QaSessionMapper;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final String ANSWER_STATUS_SUCCESS = "SUCCESS";
    private static final String ANSWER_STATUS_NO_HIT = "NO_HIT";
    private static final String TICKET_STATUS_PENDING = "PENDING";
    private static final String TICKET_STATUS_PROCESSING = "PROCESSING";
    private static final String TICKET_STATUS_WAITING_USER = "WAITING_USER";
    private static final String TICKET_STATUS_RESOLVED = "RESOLVED";
    private static final String TICKET_STATUS_CLOSED = "CLOSED";
    private static final String TICKET_SOURCE_QA_HANDOFF = "QA_HANDOFF";
    private static final String DRAFT_STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String DRAFT_STATUS_PUBLISHED = "PUBLISHED";
    private static final int QUESTION_DETAIL_MESSAGE_LIMIT = 12;
    private static final int QUESTION_DETAIL_TICKET_LIMIT = 8;
    private static final int QUESTION_DETAIL_DRAFT_LIMIT = 8;

    private final QaMessageMapper qaMessageMapper;
    private final QaSessionMapper qaSessionMapper;
    private final TicketMapper ticketMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDraftMapper knowledgeDraftMapper;
    private final UserAccountMapper userAccountMapper;
    private final CurrentUserProvider currentUserProvider;

    public DashboardServiceImpl(QaMessageMapper qaMessageMapper,
                                QaSessionMapper qaSessionMapper,
                                TicketMapper ticketMapper,
                                KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper knowledgeDocumentMapper,
                                KnowledgeDraftMapper knowledgeDraftMapper,
                                UserAccountMapper userAccountMapper,
                                CurrentUserProvider currentUserProvider) {
        this.qaMessageMapper = qaMessageMapper;
        this.qaSessionMapper = qaSessionMapper;
        this.ticketMapper = ticketMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeDraftMapper = knowledgeDraftMapper;
        this.userAccountMapper = userAccountMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public DashboardOverviewVO overview() {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();

        long knowledgeBaseCount = count(knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
        ));
        long documentCount = count(knowledgeDocumentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
        ));
        long qaCount = count(qaMessageMapper.selectCount(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
        ));
        long qaSuccessCount = count(qaMessageMapper.selectCount(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .eq(QaMessageEntity::getAnswerStatus, ANSWER_STATUS_SUCCESS)
        ));
        long qaNoHitCount = count(qaMessageMapper.selectCount(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .eq(QaMessageEntity::getAnswerStatus, ANSWER_STATUS_NO_HIT)
        ));
        long handoffCount = count(qaMessageMapper.selectCount(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .eq(QaMessageEntity::getNeedHumanHandoff, 1)
        ));
        long ticketCount = count(ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, tenantId)
        ));
        long openTicketCount = count(ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, tenantId)
                        .in(TicketEntity::getStatus, List.of(TICKET_STATUS_PENDING, TICKET_STATUS_PROCESSING, TICKET_STATUS_WAITING_USER))
        ));
        long resolvedTicketCount = count(ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, tenantId)
                        .in(TicketEntity::getStatus, List.of(TICKET_STATUS_RESOLVED, TICKET_STATUS_CLOSED))
        ));
        long draftPendingReviewCount = count(knowledgeDraftMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, tenantId)
                        .eq(KnowledgeDraftEntity::getStatus, DRAFT_STATUS_PENDING_REVIEW)
        ));
        long draftPublishedCount = count(knowledgeDraftMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, tenantId)
                        .eq(KnowledgeDraftEntity::getStatus, DRAFT_STATUS_PUBLISHED)
        ));

        return DashboardOverviewVO.builder()
                .knowledgeBaseCount(knowledgeBaseCount)
                .documentCount(documentCount)
                .qaCount(qaCount)
                .qaSuccessCount(qaSuccessCount)
                .qaNoHitCount(qaNoHitCount)
                .qaHitRate(rate(qaSuccessCount, qaCount))
                .handoffCount(handoffCount)
                .handoffRate(rate(handoffCount, qaCount))
                .ticketCount(ticketCount)
                .openTicketCount(openTicketCount)
                .resolvedTicketCount(resolvedTicketCount)
                .ticketResolveRate(rate(resolvedTicketCount, ticketCount))
                .draftPendingReviewCount(draftPendingReviewCount)
                .draftPublishedCount(draftPublishedCount)
                .build();
    }

    @Override
    public List<DashboardTrendPointVO> trends(Integer days) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        int safeDays = sanitizeDays(days, 7);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(safeDays - 1L);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        Map<LocalDate, TrendCounter> counterMap = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) {
            counterMap.put(startDate.plusDays(i), new TrendCounter());
        }

        List<QaMessageEntity> messages = qaMessageMapper.selectList(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .ge(QaMessageEntity::getCreatedAt, startDateTime)
                        .orderByAsc(QaMessageEntity::getCreatedAt)
        );
        for (QaMessageEntity message : messages) {
            LocalDate statDate = message.getCreatedAt().toLocalDate();
            TrendCounter counter = counterMap.get(statDate);
            if (counter == null) {
                continue;
            }
            counter.qaCount++;
            if (ANSWER_STATUS_SUCCESS.equals(message.getAnswerStatus())) {
                counter.successCount++;
            }
            if (ANSWER_STATUS_NO_HIT.equals(message.getAnswerStatus())) {
                counter.noHitCount++;
            }
            if (message.getNeedHumanHandoff() != null && message.getNeedHumanHandoff() == 1) {
                counter.handoffCount++;
            }
        }

        List<TicketEntity> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, tenantId)
                        .ge(TicketEntity::getCreatedAt, startDateTime)
        );
        for (TicketEntity ticket : tickets) {
            TrendCounter createdCounter = counterMap.get(ticket.getCreatedAt().toLocalDate());
            if (createdCounter != null) {
                createdCounter.ticketCreatedCount++;
            }
            if (ticket.getResolvedAt() != null) {
                TrendCounter resolvedCounter = counterMap.get(ticket.getResolvedAt().toLocalDate());
                if (resolvedCounter != null) {
                    resolvedCounter.ticketResolvedCount++;
                }
            }
        }

        List<KnowledgeDraftEntity> drafts = knowledgeDraftMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, tenantId)
                        .eq(KnowledgeDraftEntity::getStatus, DRAFT_STATUS_PUBLISHED)
                        .ge(KnowledgeDraftEntity::getPublishedAt, startDateTime)
        );
        for (KnowledgeDraftEntity draft : drafts) {
            if (draft.getPublishedAt() == null) {
                continue;
            }
            TrendCounter publishedCounter = counterMap.get(draft.getPublishedAt().toLocalDate());
            if (publishedCounter != null) {
                publishedCounter.draftPublishedCount++;
            }
        }

        return counterMap.entrySet().stream()
                .map(entry -> DashboardTrendPointVO.builder()
                        .statDate(entry.getKey())
                        .qaCount(entry.getValue().qaCount)
                        .successCount(entry.getValue().successCount)
                        .noHitCount(entry.getValue().noHitCount)
                        .handoffCount(entry.getValue().handoffCount)
                        .ticketCreatedCount(entry.getValue().ticketCreatedCount)
                        .ticketResolvedCount(entry.getValue().ticketResolvedCount)
                        .draftPublishedCount(entry.getValue().draftPublishedCount)
                        .build())
                .toList();
    }

    @Override
    public List<HotQuestionVO> hotQuestions(Integer days, Integer limit) {
        QuestionAggregationContext context = buildQuestionAggregationContext(days, false);
        return context.groups.values().stream()
                .map(group -> HotQuestionVO.builder()
                        .knowledgeBaseId(group.knowledgeBaseId)
                        .knowledgeBaseName(context.knowledgeBaseNameMap.get(group.knowledgeBaseId))
                        .questionText(group.displayQuestionText)
                        .askCount(group.askCount)
                        .successCount(group.successCount)
                        .noHitCount(group.noHitCount)
                        .handoffCount(group.handoffCount)
                        .latestAskedAt(group.latestAskedAt)
                        .build())
                .sorted((left, right) -> compareQuestionGroup(left.getAskCount(), left.getLatestAskedAt(), right.getAskCount(), right.getLatestAskedAt()))
                .limit(sanitizeLimit(limit))
                .toList();
    }

    @Override
    public List<NoHitQuestionAnalysisVO> noHitQuestions(Integer days, Integer limit) {
        QuestionAggregationContext context = buildQuestionAggregationContext(days, true);
        Map<Long, List<TicketEntity>> ticketsByQaMessageId = context.ticketsByQaMessageId;
        Map<Long, List<KnowledgeDraftEntity>> draftsByTicketId = context.draftsByTicketId;

        return context.groups.values().stream()
                .map(group -> {
                    Set<Long> relatedTicketIds = new LinkedHashSet<>();
                    long resolvedTicketCount = 0L;
                    long relatedDraftCount = 0L;
                    long publishedDraftCount = 0L;
                    for (Long qaMessageId : group.qaMessageIds) {
                        List<TicketEntity> relatedTickets = ticketsByQaMessageId.getOrDefault(qaMessageId, List.of());
                        for (TicketEntity ticket : relatedTickets) {
                            if (relatedTicketIds.add(ticket.getId())) {
                                if (isResolvedTicket(ticket)) {
                                    resolvedTicketCount++;
                                }
                                List<KnowledgeDraftEntity> relatedDrafts = draftsByTicketId.getOrDefault(ticket.getId(), List.of());
                                relatedDraftCount += relatedDrafts.size();
                                publishedDraftCount += relatedDrafts.stream()
                                        .filter(draft -> DRAFT_STATUS_PUBLISHED.equals(draft.getStatus()))
                                        .count();
                            }
                        }
                    }

                    long relatedTicketCount = relatedTicketIds.size();
                    return NoHitQuestionAnalysisVO.builder()
                            .knowledgeBaseId(group.knowledgeBaseId)
                            .knowledgeBaseName(context.knowledgeBaseNameMap.get(group.knowledgeBaseId))
                            .questionText(group.displayQuestionText)
                            .askCount(group.askCount)
                            .handoffCount(group.handoffCount)
                            .relatedTicketCount(relatedTicketCount)
                            .resolvedTicketCount(resolvedTicketCount)
                            .relatedDraftCount(relatedDraftCount)
                            .publishedDraftCount(publishedDraftCount)
                            .latestAskedAt(group.latestAskedAt)
                            .suggestedAction(suggestAction(relatedTicketCount, resolvedTicketCount, relatedDraftCount, publishedDraftCount))
                            .build();
                })
                .sorted((left, right) -> compareQuestionGroup(left.getAskCount(), left.getLatestAskedAt(), right.getAskCount(), right.getLatestAskedAt()))
                .limit(sanitizeLimit(limit))
                .toList();
    }

    @Override
    public DashboardQuestionDetailVO questionDetail(Integer days, Long knowledgeBaseId, String questionText) {
        if (!StringUtils.hasText(questionText)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Question text is required");
        }

        QuestionAggregationContext context = buildQuestionAggregationContext(days, false);
        QuestionGroup group = context.groups.get(new QuestionGroupKey(knowledgeBaseId, normalizeQuestion(questionText)));
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Dashboard question detail not found");
        }

        List<QaMessageEntity> relatedMessages = group.qaMessageIds.isEmpty()
                ? List.of()
                : qaMessageMapper.selectBatchIds(group.qaMessageIds).stream()
                .sorted(Comparator.comparing(QaMessageEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();

        Set<Long> sessionIds = relatedMessages.stream()
                .map(QaMessageEntity::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, QaSessionEntity> sessionMap = sessionIds.isEmpty()
                ? Collections.emptyMap()
                : qaSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(QaSessionEntity::getId, session -> session));

        List<TicketEntity> relatedTickets = group.qaMessageIds.stream()
                .flatMap(qaMessageId -> context.ticketsByQaMessageId.getOrDefault(qaMessageId, List.of()).stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(TicketEntity::getId, ticket -> ticket, (left, right) -> left, LinkedHashMap::new),
                        ticketMap -> ticketMap.values().stream()
                                .sorted(Comparator.comparing(TicketEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                                .toList()
                ));

        List<KnowledgeDraftEntity> relatedDrafts = relatedTickets.stream()
                .map(TicketEntity::getId)
                .flatMap(ticketId -> context.draftsByTicketId.getOrDefault(ticketId, List.of()).stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(KnowledgeDraftEntity::getId, draft -> draft, (left, right) -> left, LinkedHashMap::new),
                        draftMap -> draftMap.values().stream()
                                .sorted(Comparator.comparing(KnowledgeDraftEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                                .toList()
                ));

        Set<Long> userIds = new LinkedHashSet<>();
        relatedTickets.stream()
                .map(TicketEntity::getAssigneeUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        relatedDrafts.stream()
                .map(KnowledgeDraftEntity::getReviewerUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        Map<Long, UserAccountEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, user -> user));

        long resolvedTicketCount = relatedTickets.stream().filter(this::isResolvedTicket).count();
        long relatedDraftCount = relatedDrafts.size();
        long publishedDraftCount = relatedDrafts.stream()
                .filter(draft -> DRAFT_STATUS_PUBLISHED.equals(draft.getStatus()))
                .count();

        return DashboardQuestionDetailVO.builder()
                .knowledgeBaseId(group.knowledgeBaseId)
                .knowledgeBaseName(context.knowledgeBaseNameMap.get(group.knowledgeBaseId))
                .questionText(group.displayQuestionText)
                .askCount(group.askCount)
                .successCount(group.successCount)
                .noHitCount(group.noHitCount)
                .handoffCount(group.handoffCount)
                .relatedTicketCount((long) relatedTickets.size())
                .resolvedTicketCount(resolvedTicketCount)
                .relatedDraftCount(relatedDraftCount)
                .publishedDraftCount(publishedDraftCount)
                .latestAskedAt(group.latestAskedAt)
                .suggestedAction(suggestAction(relatedTickets.size(), resolvedTicketCount, relatedDraftCount, publishedDraftCount))
                .messages(relatedMessages.stream()
                        .limit(QUESTION_DETAIL_MESSAGE_LIMIT)
                        .map(message -> {
                            QaSessionEntity session = sessionMap.get(message.getSessionId());
                            return DashboardQuestionMessageVO.builder()
                                    .id(message.getId())
                                    .sessionId(message.getSessionId())
                                    .sessionTitle(session == null ? null : session.getSessionTitle())
                                    .questionText(message.getQuestionText())
                                    .answerText(message.getAnswerText())
                                    .answerStatus(message.getAnswerStatus())
                                    .sourceCount(message.getSourceCount())
                                    .needHumanHandoff(message.getNeedHumanHandoff() != null && message.getNeedHumanHandoff() == 1)
                                    .createdAt(message.getCreatedAt())
                                    .build();
                        })
                        .toList())
                .tickets(relatedTickets.stream()
                        .limit(QUESTION_DETAIL_TICKET_LIMIT)
                        .map(ticket -> {
                            UserAccountEntity assignee = userMap.get(ticket.getAssigneeUserId());
                            return DashboardQuestionTicketVO.builder()
                                    .id(ticket.getId())
                                    .ticketNo(ticket.getTicketNo())
                                    .sourceQaMessageId(ticket.getSourceQaMessageId())
                                    .title(ticket.getTitle())
                                    .priority(ticket.getPriority())
                                    .status(ticket.getStatus())
                                    .assigneeName(assignee == null ? null : assignee.getRealName())
                                    .createdAt(ticket.getCreatedAt())
                                    .resolvedAt(ticket.getResolvedAt())
                                    .closedAt(ticket.getClosedAt())
                                    .build();
                        })
                        .toList())
                .drafts(relatedDrafts.stream()
                        .limit(QUESTION_DETAIL_DRAFT_LIMIT)
                        .map(draft -> {
                            UserAccountEntity reviewer = userMap.get(draft.getReviewerUserId());
                            return DashboardQuestionDraftVO.builder()
                                    .id(draft.getId())
                                    .sourceTicketId(draft.getSourceTicketId())
                                    .title(draft.getTitle())
                                    .draftType(draft.getDraftType())
                                    .status(draft.getStatus())
                                    .reviewerName(reviewer == null ? null : reviewer.getRealName())
                                    .publishedDocumentId(draft.getPublishedDocumentId())
                                    .createdAt(draft.getCreatedAt())
                                    .publishedAt(draft.getPublishedAt())
                                    .build();
                        })
                        .toList())
                .build();
    }

    private QuestionAggregationContext buildQuestionAggregationContext(Integer days, boolean onlyNoHit) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        int safeDays = sanitizeDays(days, 30);
        LocalDateTime startDateTime = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        List<QaMessageEntity> messages = qaMessageMapper.selectList(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .ge(QaMessageEntity::getCreatedAt, startDateTime)
                        .eq(onlyNoHit, QaMessageEntity::getAnswerStatus, ANSWER_STATUS_NO_HIT)
                        .orderByDesc(QaMessageEntity::getCreatedAt)
        );
        if (messages.isEmpty()) {
            return QuestionAggregationContext.empty();
        }

        Set<Long> sessionIds = messages.stream()
                .map(QaMessageEntity::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, QaSessionEntity> sessionMap = sessionIds.isEmpty()
                ? Collections.emptyMap()
                : qaSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(QaSessionEntity::getId, session -> session));

        Set<Long> knowledgeBaseIds = sessionMap.values().stream()
                .map(QaSessionEntity::getKnowledgeBaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> knowledgeBaseNameMap = knowledgeBaseIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeBaseMapper.selectBatchIds(knowledgeBaseIds).stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, KnowledgeBaseEntity::getKbName));

        Map<QuestionGroupKey, QuestionGroup> groups = new LinkedHashMap<>();
        for (QaMessageEntity message : messages) {
            if (!StringUtils.hasText(message.getQuestionText())) {
                continue;
            }
            QaSessionEntity session = sessionMap.get(message.getSessionId());
            Long knowledgeBaseId = session == null ? null : session.getKnowledgeBaseId();
            String normalizedQuestion = normalizeQuestion(message.getQuestionText());
            if (!StringUtils.hasText(normalizedQuestion)) {
                continue;
            }

            QuestionGroupKey key = new QuestionGroupKey(knowledgeBaseId, normalizedQuestion);
            QuestionGroup group = groups.computeIfAbsent(key, ignored -> new QuestionGroup());
            group.knowledgeBaseId = knowledgeBaseId;
            group.askCount++;
            group.qaMessageIds.add(message.getId());
            if (ANSWER_STATUS_SUCCESS.equals(message.getAnswerStatus())) {
                group.successCount++;
            }
            if (ANSWER_STATUS_NO_HIT.equals(message.getAnswerStatus())) {
                group.noHitCount++;
            }
            if (message.getNeedHumanHandoff() != null && message.getNeedHumanHandoff() == 1) {
                group.handoffCount++;
            }
            if (group.latestAskedAt == null || message.getCreatedAt().isAfter(group.latestAskedAt)) {
                group.latestAskedAt = message.getCreatedAt();
                group.displayQuestionText = message.getQuestionText().trim();
            }
        }

        Set<Long> qaMessageIds = messages.stream()
                .map(QaMessageEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<TicketEntity>> ticketsByQaMessageId = ticketMapper.selectList(
                        new LambdaQueryWrapper<TicketEntity>()
                                .eq(TicketEntity::getTenantId, tenantId)
                                .eq(TicketEntity::getSourceType, TICKET_SOURCE_QA_HANDOFF)
                                .in(!qaMessageIds.isEmpty(), TicketEntity::getSourceQaMessageId, qaMessageIds)
                ).stream()
                .filter(ticket -> ticket.getSourceQaMessageId() != null)
                .collect(Collectors.groupingBy(TicketEntity::getSourceQaMessageId));

        Set<Long> ticketIds = ticketsByQaMessageId.values().stream()
                .flatMap(Collection::stream)
                .map(TicketEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<KnowledgeDraftEntity>> draftsByTicketId = ticketIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeDraftMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDraftEntity>()
                                .eq(KnowledgeDraftEntity::getTenantId, tenantId)
                                .in(KnowledgeDraftEntity::getSourceTicketId, ticketIds)
                ).stream().collect(Collectors.groupingBy(KnowledgeDraftEntity::getSourceTicketId));

        return new QuestionAggregationContext(groups, knowledgeBaseNameMap, ticketsByQaMessageId, draftsByTicketId);
    }

    private int compareQuestionGroup(Long leftCount, LocalDateTime leftLatest, Long rightCount, LocalDateTime rightLatest) {
        int compareCount = Long.compare(rightCount == null ? 0L : rightCount, leftCount == null ? 0L : leftCount);
        if (compareCount != 0) {
            return compareCount;
        }
        LocalDateTime safeLeftLatest = leftLatest == null ? LocalDateTime.MIN : leftLatest;
        LocalDateTime safeRightLatest = rightLatest == null ? LocalDateTime.MIN : rightLatest;
        return safeRightLatest.compareTo(safeLeftLatest);
    }

    private boolean isResolvedTicket(TicketEntity ticket) {
        return TICKET_STATUS_RESOLVED.equals(ticket.getStatus()) || TICKET_STATUS_CLOSED.equals(ticket.getStatus());
    }

    private String suggestAction(long relatedTicketCount, long resolvedTicketCount, long relatedDraftCount, long publishedDraftCount) {
        if (publishedDraftCount > 0) {
            return "Knowledge has already been published for this topic. Keep monitoring whether the hit rate improves.";
        }
        if (relatedDraftCount > 0) {
            return "A knowledge draft already exists for this topic. Prioritize review and publishing to reduce repeated no-hit questions.";
        }
        if (resolvedTicketCount > 0) {
            return "Resolved tickets already exist for this topic. Publish a knowledge draft as soon as possible.";
        }
        if (relatedTicketCount > 0) {
            return "This topic often escalates to manual support. Prioritize FAQ or SOP enrichment for it.";
        }
        return "This topic has not been converted into tickets yet. Collect more samples and enrich the current knowledge base.";
    }

    private String normalizeQuestion(String questionText) {
        return questionText.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int sanitizeDays(Integer days, int defaultValue) {
        if (days == null || days < 1) {
            return defaultValue;
        }
        return Math.min(days, 90);
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round(((double) numerator * 10000D) / denominator) / 100D;
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private static final class TrendCounter {
        private long qaCount;
        private long successCount;
        private long noHitCount;
        private long handoffCount;
        private long ticketCreatedCount;
        private long ticketResolvedCount;
        private long draftPublishedCount;
    }

    private static final class QuestionGroup {
        private Long knowledgeBaseId;
        private String displayQuestionText;
        private long askCount;
        private long successCount;
        private long noHitCount;
        private long handoffCount;
        private LocalDateTime latestAskedAt;
        private final List<Long> qaMessageIds = new ArrayList<>();
    }

    private record QuestionGroupKey(Long knowledgeBaseId, String normalizedQuestion) {
    }

    private record QuestionAggregationContext(
            Map<QuestionGroupKey, QuestionGroup> groups,
            Map<Long, String> knowledgeBaseNameMap,
            Map<Long, List<TicketEntity>> ticketsByQaMessageId,
            Map<Long, List<KnowledgeDraftEntity>> draftsByTicketId
    ) {
        private static QuestionAggregationContext empty() {
            return new QuestionAggregationContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
