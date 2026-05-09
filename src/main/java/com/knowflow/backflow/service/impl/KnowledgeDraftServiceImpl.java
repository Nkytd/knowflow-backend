package com.knowflow.backflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.backflow.dto.CreateKnowledgeDraftFromTicketRequest;
import com.knowflow.backflow.dto.ReviewKnowledgeDraftRequest;
import com.knowflow.backflow.dto.UpdateKnowledgeDraftRequest;
import com.knowflow.backflow.vo.KnowledgeBaseOptionVO;
import com.knowflow.backflow.entity.KnowledgeDraftEntity;
import com.knowflow.backflow.mapper.KnowledgeDraftMapper;
import com.knowflow.backflow.service.KnowledgeDraftService;
import com.knowflow.backflow.vo.KnowledgeDraftVO;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.integration.storage.FileStorageClient;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.parser.service.ParseTaskService;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.ticket.entity.TicketCommentEntity;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.mapper.TicketCommentMapper;
import com.knowflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeDraftServiceImpl implements KnowledgeDraftService {

    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String DRAFT_TYPE_FAQ = "FAQ";
    private static final Set<String> DRAFT_TYPES = Set.of(DRAFT_TYPE_FAQ, "ARTICLE");
    private static final String TICKET_STATUS_RESOLVED = "RESOLVED";
    private static final String TICKET_STATUS_CLOSED = "CLOSED";
    private static final String COMMENT_SOLUTION = "SOLUTION";
    private static final String COMMENT_AGENT_REPLY = "AGENT_REPLY";
    private static final String DOCUMENT_SOURCE_TICKET_BACKFLOW = "TICKET_BACKFLOW";
    private static final String DOCUMENT_STATUS_ENABLED = "ENABLED";
    private static final String TASK_STATUS_PENDING = "PENDING";

    private final KnowledgeDraftMapper knowledgeDraftMapper;
    private final TicketMapper ticketMapper;
    private final TicketCommentMapper ticketCommentMapper;
    private final QaMessageMapper qaMessageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UserAccountMapper userAccountMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FileStorageClient fileStorageClient;
    private final ParseTaskService parseTaskService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final CurrentUserProvider currentUserProvider;

    public KnowledgeDraftServiceImpl(KnowledgeDraftMapper knowledgeDraftMapper,
                                     TicketMapper ticketMapper,
                                     TicketCommentMapper ticketCommentMapper,
                                     QaMessageMapper qaMessageMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     UserAccountMapper userAccountMapper,
                                     KnowledgeDocumentMapper knowledgeDocumentMapper,
                                     FileStorageClient fileStorageClient,
                                     ParseTaskService parseTaskService,
                                     KnowledgeBaseService knowledgeBaseService,
                                     CurrentUserProvider currentUserProvider) {
        this.knowledgeDraftMapper = knowledgeDraftMapper;
        this.ticketMapper = ticketMapper;
        this.ticketCommentMapper = ticketCommentMapper;
        this.qaMessageMapper = qaMessageMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.userAccountMapper = userAccountMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.fileStorageClient = fileStorageClient;
        this.parseTaskService = parseTaskService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public KnowledgeDraftVO createFromTicket(Long ticketId, CreateKnowledgeDraftFromTicketRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = getTicketForCurrentTenant(ticketId);
        ensureTicketResolved(ticket);
        knowledgeBaseService.validateKnowledgeBaseExists(currentUser.tenantId(), request.getKnowledgeBaseId());

        KnowledgeDraftEntity existing = knowledgeDraftMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, currentUser.tenantId())
                        .eq(KnowledgeDraftEntity::getSourceTicketId, ticketId)
                        .last("limit 1")
        );
        if (existing != null) {
            return toVO(existing);
        }

        QaMessageEntity qaMessage = findQaMessage(ticket.getSourceQaMessageId());
        String questionText = resolveQuestionText(ticket, qaMessage);
        String answerText = buildDraftAnswer(ticket);

        KnowledgeDraftEntity entity = new KnowledgeDraftEntity();
        entity.setTenantId(currentUser.tenantId());
        entity.setSourceTicketId(ticketId);
        entity.setKnowledgeBaseId(request.getKnowledgeBaseId());
        entity.setDraftType(resolveDraftType(request.getDraftType()));
        entity.setTitle(resolveDraftTitle(request.getTitle(), ticket, questionText));
        entity.setQuestionText(questionText);
        entity.setAnswerText(answerText);
        entity.setStatus(STATUS_PENDING_REVIEW);
        knowledgeDraftMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public PageResponse<KnowledgeDraftVO> page(Integer pageNo, Integer pageSize, Long knowledgeBaseId, String status, String draftType) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String normalizedDraftType = StringUtils.hasText(draftType) ? draftType.trim().toUpperCase() : null;
        Page<KnowledgeDraftEntity> page = knowledgeDraftMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, currentUser.tenantId())
                        .eq(knowledgeBaseId != null, KnowledgeDraftEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(StringUtils.hasText(status), KnowledgeDraftEntity::getStatus, status)
                        .eq(StringUtils.hasText(normalizedDraftType), KnowledgeDraftEntity::getDraftType, normalizedDraftType)
                        .orderByDesc(KnowledgeDraftEntity::getUpdatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                buildVOs(page.getRecords())
        );
    }

    @Override
    public List<KnowledgeBaseOptionVO> listKnowledgeBaseOptions() {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseEntity>()
                                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                                .orderByDesc(KnowledgeBaseEntity::getStatus)
                                .orderByAsc(KnowledgeBaseEntity::getKbName)
                ).stream()
                .map(knowledgeBase -> KnowledgeBaseOptionVO.builder()
                        .id(knowledgeBase.getId())
                        .kbCode(knowledgeBase.getKbCode())
                        .kbName(knowledgeBase.getKbName())
                        .status(knowledgeBase.getStatus())
                        .docCount(knowledgeBase.getDocCount())
                        .build())
                .toList();
    }

    @Override
    public KnowledgeDraftVO getById(Long id) {
        return toVO(getDraftForCurrentTenant(id));
    }

    @Override
    @Transactional
    public KnowledgeDraftVO update(Long id, UpdateKnowledgeDraftRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeDraftEntity entity = getDraftForCurrentTenant(id);
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Published knowledge draft cannot be edited");
        }

        knowledgeBaseService.validateKnowledgeBaseExists(currentUser.tenantId(), request.getKnowledgeBaseId());
        entity.setKnowledgeBaseId(request.getKnowledgeBaseId());
        entity.setDraftType(resolveDraftType(request.getDraftType()));
        entity.setTitle(request.getTitle().trim());
        entity.setQuestionText(request.getQuestionText().trim());
        entity.setAnswerText(request.getAnswerText().trim());
        entity.setStatus(STATUS_PENDING_REVIEW);
        entity.setReviewerUserId(null);
        entity.setReviewRemark(null);
        knowledgeDraftMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public KnowledgeDraftVO approve(Long id, ReviewKnowledgeDraftRequest request) {
        KnowledgeDraftEntity entity = getDraftForCurrentTenant(id);
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Published knowledge draft cannot be approved again");
        }

        entity.setStatus(STATUS_APPROVED);
        entity.setReviewerUserId(currentUserProvider.getCurrentUser().userId());
        entity.setReviewRemark(trimToLength(request.getReviewRemark(), 1000));
        knowledgeDraftMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public KnowledgeDraftVO reject(Long id, ReviewKnowledgeDraftRequest request) {
        KnowledgeDraftEntity entity = getDraftForCurrentTenant(id);
        if (STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Published knowledge draft cannot be rejected");
        }

        entity.setStatus(STATUS_REJECTED);
        entity.setReviewerUserId(currentUserProvider.getCurrentUser().userId());
        entity.setReviewRemark(trimToLength(request.getReviewRemark(), 1000));
        knowledgeDraftMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public KnowledgeDraftVO publish(Long id) {
        KnowledgeDraftEntity entity = getDraftForCurrentTenant(id);
        if (!STATUS_APPROVED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only approved knowledge draft can be published");
        }
        if (entity.getPublishedDocumentId() != null) {
            throw new BizException(ErrorCode.CONFLICT, "Knowledge draft has already been published");
        }

        TicketEntity ticket = getTicketForCurrentTenant(entity.getSourceTicketId());
        knowledgeBaseService.validateKnowledgeBaseExists(entity.getTenantId(), entity.getKnowledgeBaseId());

        String docCode = CodeGenerator.documentCode();
        String markdown = buildPublishedMarkdown(entity, ticket);
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        String objectName = String.format(
                "tenant-%d/kb-%d/%s-%s.md",
                entity.getTenantId(),
                entity.getKnowledgeBaseId(),
                docCode,
                "ticket-backflow"
        );
        String storagePath = fileStorageClient.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, "text/markdown");

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setTenantId(entity.getTenantId());
        document.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        document.setDocCode(docCode);
        document.setDocName(buildDocumentName(entity));
        document.setSourceType(DOCUMENT_SOURCE_TICKET_BACKFLOW);
        document.setStorageType(fileStorageClient.storageType());
        document.setStoragePath(storagePath);
        document.setFileType("md");
        document.setFileSize((long) bytes.length);
        document.setVersionNo(1);
        document.setStatus(DOCUMENT_STATUS_ENABLED);
        document.setParseStatus(TASK_STATUS_PENDING);
        document.setIndexStatus(TASK_STATUS_PENDING);
        document.setChunkCount(0);
        knowledgeDocumentMapper.insert(document);
        parseTaskService.createPendingParseTask(document);
        knowledgeBaseService.refreshDocumentCount(entity.getTenantId(), entity.getKnowledgeBaseId());

        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishedDocumentId(document.getId());
        entity.setPublishedAt(LocalDateTime.now());
        knowledgeDraftMapper.updateById(entity);
        return toVO(entity);
    }

    private KnowledgeDraftEntity getDraftForCurrentTenant(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        KnowledgeDraftEntity entity = knowledgeDraftMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getId, id)
                        .eq(KnowledgeDraftEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge draft not found");
        }
        return entity;
    }

    private TicketEntity getTicketForCurrentTenant(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        TicketEntity entity = ticketMapper.selectOne(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getId, id)
                        .eq(TicketEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Ticket not found");
        }
        return entity;
    }

    private void ensureTicketResolved(TicketEntity ticket) {
        if (!TICKET_STATUS_RESOLVED.equals(ticket.getStatus()) && !TICKET_STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only resolved or closed ticket can generate knowledge draft");
        }
    }

    private QaMessageEntity findQaMessage(Long qaMessageId) {
        if (qaMessageId == null) {
            return null;
        }
        return qaMessageMapper.selectById(qaMessageId);
    }

    private String resolveQuestionText(TicketEntity ticket, QaMessageEntity qaMessage) {
        if (qaMessage != null && StringUtils.hasText(qaMessage.getQuestionText())) {
            return trimToLength(qaMessage.getQuestionText().trim(), 1000);
        }
        if (StringUtils.hasText(ticket.getTitle())) {
            return trimToLength(ticket.getTitle().trim(), 1000);
        }
        return "How should this issue be handled?";
    }

    private String buildDraftAnswer(TicketEntity ticket) {
        List<TicketCommentEntity> comments = ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketCommentEntity>()
                        .eq(TicketCommentEntity::getTicketId, ticket.getId())
                        .orderByAsc(TicketCommentEntity::getCreatedAt)
        );

        TicketCommentEntity solution = comments.stream()
                .filter(comment -> COMMENT_SOLUTION.equals(comment.getCommentType()))
                .reduce((first, second) -> second)
                .orElse(null);

        StringBuilder builder = new StringBuilder();
        if (solution != null && StringUtils.hasText(solution.getContent())) {
            builder.append(solution.getContent().trim());
        } else {
            builder.append("Please follow the ticket resolution notes to handle this issue.");
        }

        List<String> highlights = comments.stream()
                .filter(comment -> COMMENT_AGENT_REPLY.equals(comment.getCommentType()))
                .map(TicketCommentEntity::getContent)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .toList();

        if (!highlights.isEmpty()) {
            builder.append("\n\nHandling notes:\n");
            for (String highlight : highlights) {
                builder.append("- ").append(highlight).append("\n");
            }
        }

        if (StringUtils.hasText(ticket.getContent())) {
            builder.append("\nOriginal issue context:\n")
                    .append(ticket.getContent().trim());
        }
        return builder.toString().trim();
    }

    private String resolveDraftType(String draftType) {
        if (!StringUtils.hasText(draftType)) {
            return DRAFT_TYPE_FAQ;
        }
        String normalized = draftType.trim().toUpperCase();
        if (!DRAFT_TYPES.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported knowledge draft type");
        }
        return normalized;
    }

    private String resolveDraftTitle(String customTitle, TicketEntity ticket, String questionText) {
        if (StringUtils.hasText(customTitle)) {
            return customTitle.trim();
        }
        if (StringUtils.hasText(ticket.getTitle())) {
            return ticket.getTitle().trim();
        }
        return trimToLength(questionText, 255);
    }

    private String buildPublishedMarkdown(KnowledgeDraftEntity draft, TicketEntity ticket) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(draft.getTitle()).append("\n\n");
        builder.append("## Question\n");
        builder.append(draft.getQuestionText()).append("\n\n");
        builder.append("## Answer\n");
        builder.append(draft.getAnswerText()).append("\n\n");
        builder.append("## Source Ticket\n");
        builder.append("- Ticket No: ").append(ticket.getTicketNo()).append("\n");
        builder.append("- Ticket Title: ").append(defaultText(ticket.getTitle())).append("\n");
        builder.append("- Resolved At: ");
        if (ticket.getResolvedAt() != null) {
            builder.append(ticket.getResolvedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            builder.append("N/A");
        }
        builder.append("\n");
        return builder.toString();
    }

    private String buildDocumentName(KnowledgeDraftEntity draft) {
        String safeTitle = draft.getTitle().replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        if (!StringUtils.hasText(safeTitle)) {
            safeTitle = "ticket-backflow";
        }
        return trimToLength(safeTitle, 120) + ".md";
    }

    private List<KnowledgeDraftVO> buildVOs(List<KnowledgeDraftEntity> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }

        Set<Long> ticketIds = new LinkedHashSet<>();
        Set<Long> kbIds = new LinkedHashSet<>();
        Set<Long> reviewerIds = new LinkedHashSet<>();
        Set<Long> qaMessageIds = new LinkedHashSet<>();
        for (KnowledgeDraftEntity draft : drafts) {
            if (draft.getSourceTicketId() != null) {
                ticketIds.add(draft.getSourceTicketId());
            }
            if (draft.getKnowledgeBaseId() != null) {
                kbIds.add(draft.getKnowledgeBaseId());
            }
            if (draft.getReviewerUserId() != null) {
                reviewerIds.add(draft.getReviewerUserId());
            }
        }

        Map<Long, TicketEntity> ticketMap = ticketIds.isEmpty()
                ? Collections.emptyMap()
                : ticketMapper.selectBatchIds(ticketIds).stream()
                .collect(Collectors.toMap(TicketEntity::getId, ticket -> ticket));
        for (TicketEntity ticket : ticketMap.values()) {
            if (ticket.getSourceQaMessageId() != null) {
                qaMessageIds.add(ticket.getSourceQaMessageId());
            }
        }
        Map<Long, KnowledgeBaseEntity> kbMap = kbIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeBaseMapper.selectBatchIds(kbIds).stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, kb -> kb));
        Map<Long, UserAccountEntity> reviewerMap = reviewerIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(reviewerIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, user -> user));
        Map<Long, QaMessageEntity> qaMessageMap = qaMessageIds.isEmpty()
                ? Collections.emptyMap()
                : qaMessageMapper.selectBatchIds(qaMessageIds).stream()
                .collect(Collectors.toMap(QaMessageEntity::getId, qaMessage -> qaMessage));

        List<KnowledgeDraftVO> result = new ArrayList<>(drafts.size());
        for (KnowledgeDraftEntity draft : drafts) {
            result.add(toVO(draft, ticketMap, kbMap, reviewerMap, qaMessageMap));
        }
        return result;
    }

    private KnowledgeDraftVO toVO(KnowledgeDraftEntity draft) {
        return buildVOs(List.of(draft)).get(0);
    }

    private KnowledgeDraftVO toVO(KnowledgeDraftEntity draft,
                                  Map<Long, TicketEntity> ticketMap,
                                  Map<Long, KnowledgeBaseEntity> kbMap,
                                  Map<Long, UserAccountEntity> reviewerMap,
                                  Map<Long, QaMessageEntity> qaMessageMap) {
        TicketEntity ticket = ticketMap.get(draft.getSourceTicketId());
        KnowledgeBaseEntity knowledgeBase = kbMap.get(draft.getKnowledgeBaseId());
        UserAccountEntity reviewer = reviewerMap.get(draft.getReviewerUserId());
        QaMessageEntity qaMessage = ticket == null ? null : qaMessageMap.get(ticket.getSourceQaMessageId());
        return KnowledgeDraftVO.builder()
                .id(draft.getId())
                .sourceTicketId(draft.getSourceTicketId())
                .sourceTicketNo(ticket == null ? null : ticket.getTicketNo())
                .sourceTicketTitle(ticket == null ? null : ticket.getTitle())
                .sourceQaMessageId(ticket == null ? null : ticket.getSourceQaMessageId())
                .sourceQuestionText(qaMessage == null ? null : qaMessage.getQuestionText())
                .knowledgeBaseId(draft.getKnowledgeBaseId())
                .knowledgeBaseName(knowledgeBase == null ? null : knowledgeBase.getKbName())
                .draftType(draft.getDraftType())
                .title(draft.getTitle())
                .questionText(draft.getQuestionText())
                .answerText(draft.getAnswerText())
                .status(draft.getStatus())
                .reviewerUserId(draft.getReviewerUserId())
                .reviewerName(reviewer == null ? null : reviewer.getRealName())
                .reviewRemark(draft.getReviewRemark())
                .publishedDocumentId(draft.getPublishedDocumentId())
                .publishedAt(draft.getPublishedAt())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "N/A";
    }

    private String trimToLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
