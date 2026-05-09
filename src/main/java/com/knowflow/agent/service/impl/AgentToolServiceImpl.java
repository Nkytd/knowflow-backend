package com.knowflow.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.agent.dto.AgentToolExecuteRequest;
import com.knowflow.agent.service.AgentToolService;
import com.knowflow.agent.vo.AgentKnowledgeDraftSuggestionVO;
import com.knowflow.agent.vo.AgentToolDefinitionVO;
import com.knowflow.agent.vo.AgentToolExecutionVO;
import com.knowflow.backflow.dto.CreateKnowledgeDraftFromTicketRequest;
import com.knowflow.backflow.entity.KnowledgeDraftEntity;
import com.knowflow.backflow.mapper.KnowledgeDraftMapper;
import com.knowflow.backflow.service.KnowledgeDraftService;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.qa.mapper.QaSessionMapper;
import com.knowflow.ticket.dto.CreateQaHandoffTicketRequest;
import com.knowflow.ticket.entity.TicketCommentEntity;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.mapper.TicketCommentMapper;
import com.knowflow.ticket.mapper.TicketMapper;
import com.knowflow.ticket.service.TicketService;
import com.knowflow.ticket.vo.TicketVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentToolServiceImpl implements AgentToolService {

    private static final String QUERY_TICKET_PROGRESS = "QUERY_TICKET_PROGRESS";
    private static final String CREATE_TICKET_FROM_QA = "CREATE_TICKET_FROM_QA";
    private static final String LIST_MY_OPEN_TICKETS = "LIST_MY_OPEN_TICKETS";
    private static final String LIST_KNOWLEDGE_BASES = "LIST_KNOWLEDGE_BASES";
    private static final String SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET = "SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET";
    private static final String GENERATE_KNOWLEDGE_DRAFT_FROM_TICKET = "GENERATE_KNOWLEDGE_DRAFT_FROM_TICKET";
    private static final Set<String> ADMIN_ROLES = Set.of("TENANT_ADMIN", "SUPPORT_AGENT", "KNOWLEDGE_OPERATOR");
    private static final Set<String> RESOLVED_TICKET_STATUSES = Set.of("RESOLVED", "CLOSED");
    private static final String COMMENT_SOLUTION = "SOLUTION";
    private static final String COMMENT_AGENT_REPLY = "AGENT_REPLY";

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;
    private final TicketCommentMapper ticketCommentMapper;
    private final KnowledgeDraftService knowledgeDraftService;
    private final KnowledgeDraftMapper knowledgeDraftMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final QaMessageMapper qaMessageMapper;
    private final QaSessionMapper qaSessionMapper;
    private final CurrentUserProvider currentUserProvider;

    public AgentToolServiceImpl(TicketService ticketService,
                                TicketMapper ticketMapper,
                                TicketCommentMapper ticketCommentMapper,
                                KnowledgeDraftService knowledgeDraftService,
                                KnowledgeDraftMapper knowledgeDraftMapper,
                                KnowledgeBaseMapper knowledgeBaseMapper,
                                QaMessageMapper qaMessageMapper,
                                QaSessionMapper qaSessionMapper,
                                CurrentUserProvider currentUserProvider) {
        this.ticketService = ticketService;
        this.ticketMapper = ticketMapper;
        this.ticketCommentMapper = ticketCommentMapper;
        this.knowledgeDraftService = knowledgeDraftService;
        this.knowledgeDraftMapper = knowledgeDraftMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.qaMessageMapper = qaMessageMapper;
        this.qaSessionMapper = qaSessionMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public List<AgentToolDefinitionVO> listTools() {
        return List.of(
                tool(QUERY_TICKET_PROGRESS, "查询工单进度", "根据 ticketId 或 ticketNo 查询当前用户可见的工单状态、负责人和 SLA。", "登录用户",
                        List.of("ticketId: 工单 ID，可选", "ticketNo: 工单编号，可选")),
                tool(CREATE_TICKET_FROM_QA, "从问答创建工单", "把无法回答或低置信度的问答记录转成人工工单。", "登录用户",
                        List.of("qaMessageId: 问答消息 ID，必填", "title/content/priority: 可选")),
                tool(LIST_MY_OPEN_TICKETS, "查询我的未完结工单", "返回当前用户创建的待处理、处理中、等待补充的工单。", "登录用户",
                        List.of("pageSize: 返回数量，可选，默认 5")),
                tool(LIST_KNOWLEDGE_BASES, "查询可用知识库", "返回当前租户启用中的知识库，便于 Agent 决定后续问答范围。", "登录用户",
                        List.of("无必填参数")),
                tool(SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET, "建议工单知识草稿", "根据已解决工单、来源问答和处理评论生成知识草稿建议，不直接落库。", "TENANT_ADMIN / KNOWLEDGE_OPERATOR / SUPPORT_AGENT",
                        List.of("ticketId: 工单 ID，必填", "knowledgeBaseId: 目标知识库 ID，可选")),
                tool(GENERATE_KNOWLEDGE_DRAFT_FROM_TICKET, "从已解决工单生成知识草稿", "把已解决工单沉淀为知识草稿，供运营审核发布。", "TENANT_ADMIN / KNOWLEDGE_OPERATOR / SUPPORT_AGENT",
                        List.of("ticketId: 工单 ID，必填", "knowledgeBaseId: 知识库 ID，必填", "draftType/title: 可选"))
        );
    }

    @Override
    public AgentToolExecutionVO execute(AgentToolExecuteRequest request) {
        String toolCode = normalizeToolCode(request.getToolCode());
        Map<String, Object> arguments = request.getArguments() == null ? Map.of() : request.getArguments();
        Object result = switch (toolCode) {
            case QUERY_TICKET_PROGRESS -> queryTicketProgress(arguments);
            case CREATE_TICKET_FROM_QA -> createTicketFromQa(arguments);
            case LIST_MY_OPEN_TICKETS -> listMyOpenTickets(arguments);
            case LIST_KNOWLEDGE_BASES -> listKnowledgeBases();
            case SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET -> suggestKnowledgeDraftFromTicket(arguments);
            case GENERATE_KNOWLEDGE_DRAFT_FROM_TICKET -> generateDraftFromTicket(arguments);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported agent tool: " + request.getToolCode());
        };
        return AgentToolExecutionVO.builder()
                .toolCode(toolCode)
                .toolName(resolveToolName(toolCode))
                .success(true)
                .summary(resolveSummary(toolCode, result))
                .result(result)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private TicketVO queryTicketProgress(Map<String, Object> arguments) {
        Long ticketId = longArg(arguments, "ticketId");
        if (ticketId == null) {
            ticketId = findTicketIdByNo(stringArg(arguments, "ticketNo"));
        }
        if (ticketId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "ticketId or ticketNo is required");
        }
        return isAdminUser() ? ticketService.getAdminById(ticketId) : ticketService.getMineById(ticketId);
    }

    private TicketVO createTicketFromQa(Map<String, Object> arguments) {
        Long qaMessageId = longArg(arguments, "qaMessageId");
        if (qaMessageId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "qaMessageId is required");
        }
        CreateQaHandoffTicketRequest request = new CreateQaHandoffTicketRequest();
        request.setTitle(stringArg(arguments, "title"));
        request.setContent(stringArg(arguments, "content"));
        request.setPriority(stringArg(arguments, "priority"));
        return ticketService.createFromQaMessage(qaMessageId, request);
    }

    private Object listMyOpenTickets(Map<String, Object> arguments) {
        int pageSize = Math.max(1, Math.min(20, intArg(arguments, "pageSize", 5)));
        return ticketService.pageMine(1, pageSize, null).getRecords().stream()
                .filter(ticket -> !"RESOLVED".equals(ticket.getStatus()) && !"CLOSED".equals(ticket.getStatus()))
                .toList();
    }

    private Object listKnowledgeBases() {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                        .eq(KnowledgeBaseEntity::getStatus, "ENABLED")
                        .orderByAsc(KnowledgeBaseEntity::getKbName)
        );
    }

    private AgentKnowledgeDraftSuggestionVO suggestKnowledgeDraftFromTicket(Map<String, Object> arguments) {
        ensureAdminToolAllowed();
        Long ticketId = longArg(arguments, "ticketId");
        if (ticketId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "ticketId is required");
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = getTicketForCurrentTenant(ticketId);
        if (!RESOLVED_TICKET_STATUSES.contains(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only resolved or closed ticket can suggest knowledge draft");
        }

        KnowledgeDraftEntity existingDraft = knowledgeDraftMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDraftEntity>()
                        .eq(KnowledgeDraftEntity::getTenantId, currentUser.tenantId())
                        .eq(KnowledgeDraftEntity::getSourceTicketId, ticketId)
                        .last("limit 1")
        );
        QaMessageEntity qaMessage = ticket.getSourceQaMessageId() == null ? null : qaMessageMapper.selectById(ticket.getSourceQaMessageId());
        KnowledgeBaseEntity knowledgeBase = resolveSuggestedKnowledgeBase(currentUser.tenantId(), arguments, qaMessage);
        List<TicketCommentEntity> comments = listTicketComments(ticket);
        String questionText = resolveSuggestedQuestion(ticket, qaMessage);
        String answerText = buildSuggestedAnswer(ticket, comments);
        String title = resolveSuggestedTitle(ticket, questionText);
        List<String> reasons = buildSuggestionReasons(ticket, qaMessage, comments, knowledgeBase, existingDraft);
        double confidence = resolveSuggestionConfidence(ticket, qaMessage, comments, knowledgeBase, existingDraft);

        Map<String, Object> createArguments = new LinkedHashMap<>();
        createArguments.put("ticketId", ticketId);
        createArguments.put("knowledgeBaseId", knowledgeBase == null ? null : knowledgeBase.getId());
        createArguments.put("draftType", "FAQ");
        createArguments.put("title", title);

        return AgentKnowledgeDraftSuggestionVO.builder()
                .ticketId(ticket.getId())
                .ticketNo(ticket.getTicketNo())
                .existingDraftId(existingDraft == null ? null : existingDraft.getId())
                .knowledgeBaseId(knowledgeBase == null ? null : knowledgeBase.getId())
                .knowledgeBaseName(knowledgeBase == null ? null : knowledgeBase.getKbName())
                .draftType("FAQ")
                .title(title)
                .questionText(questionText)
                .answerText(answerText)
                .confidence(confidence)
                .reasons(reasons)
                .nextAction(existingDraft == null
                        ? "Review the suggestion, then execute GENERATE_KNOWLEDGE_DRAFT_FROM_TICKET or use the ticket page draft form."
                        : "A related draft already exists. Open the draft and continue review instead of creating a duplicate.")
                .createDraftArguments(createArguments)
                .build();
    }

    private Object generateDraftFromTicket(Map<String, Object> arguments) {
        ensureAdminToolAllowed();
        Long ticketId = longArg(arguments, "ticketId");
        Long knowledgeBaseId = longArg(arguments, "knowledgeBaseId");
        if (ticketId == null || knowledgeBaseId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "ticketId and knowledgeBaseId are required");
        }
        CreateKnowledgeDraftFromTicketRequest request = new CreateKnowledgeDraftFromTicketRequest();
        request.setKnowledgeBaseId(knowledgeBaseId);
        request.setDraftType(stringArg(arguments, "draftType"));
        request.setTitle(stringArg(arguments, "title"));
        return knowledgeDraftService.createFromTicket(ticketId, request);
    }

    private TicketEntity getTicketForCurrentTenant(Long ticketId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getId, ticketId)
                        .eq(TicketEntity::getTenantId, currentUser.tenantId())
                        .last("limit 1")
        );
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Ticket not found");
        }
        return ticket;
    }

    private KnowledgeBaseEntity resolveSuggestedKnowledgeBase(Long tenantId, Map<String, Object> arguments, QaMessageEntity qaMessage) {
        Long explicitKnowledgeBaseId = longArg(arguments, "knowledgeBaseId");
        if (explicitKnowledgeBaseId != null) {
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeBaseEntity>()
                            .eq(KnowledgeBaseEntity::getId, explicitKnowledgeBaseId)
                            .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                            .last("limit 1")
            );
            if (knowledgeBase == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "Knowledge base not found");
            }
            return knowledgeBase;
        }

        KnowledgeBaseEntity qaKnowledgeBase = findQaKnowledgeBase(tenantId, qaMessage);
        if (qaKnowledgeBase != null) {
            return qaKnowledgeBase;
        }
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseEntity>()
                                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                                .eq(KnowledgeBaseEntity::getStatus, "ENABLED")
                                .orderByDesc(KnowledgeBaseEntity::getDocCount)
                                .orderByAsc(KnowledgeBaseEntity::getKbName)
                ).stream()
                .findFirst()
                .orElse(null);
    }

    private KnowledgeBaseEntity findQaKnowledgeBase(Long tenantId, QaMessageEntity qaMessage) {
        if (qaMessage == null || qaMessage.getSessionId() == null) {
            return null;
        }
        QaSessionEntity session = qaSessionMapper.selectById(qaMessage.getSessionId());
        if (session == null || session.getKnowledgeBaseId() == null || !tenantId.equals(session.getTenantId())) {
            return null;
        }
        return knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getId, session.getKnowledgeBaseId())
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
    }

    private List<TicketCommentEntity> listTicketComments(TicketEntity ticket) {
        return ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketCommentEntity>()
                        .eq(TicketCommentEntity::getTenantId, ticket.getTenantId())
                        .eq(TicketCommentEntity::getTicketId, ticket.getId())
                        .orderByAsc(TicketCommentEntity::getCreatedAt)
        );
    }

    private String resolveSuggestedQuestion(TicketEntity ticket, QaMessageEntity qaMessage) {
        if (qaMessage != null && StringUtils.hasText(qaMessage.getQuestionText())) {
            return trimToLength(qaMessage.getQuestionText().trim(), 1000);
        }
        if (StringUtils.hasText(ticket.getTitle())) {
            return trimToLength(ticket.getTitle().trim(), 1000);
        }
        return "How should this issue be handled?";
    }

    private String buildSuggestedAnswer(TicketEntity ticket, List<TicketCommentEntity> comments) {
        TicketCommentEntity solution = comments.stream()
                .filter(comment -> COMMENT_SOLUTION.equals(comment.getCommentType()))
                .max(Comparator.comparing(TicketCommentEntity::getCreatedAt))
                .orElse(null);

        StringBuilder builder = new StringBuilder();
        if (solution != null && StringUtils.hasText(solution.getContent())) {
            builder.append(solution.getContent().trim());
        } else {
            builder.append("Please follow the resolved ticket handling notes for this issue.");
        }

        List<String> handlingNotes = comments.stream()
                .filter(comment -> COMMENT_AGENT_REPLY.equals(comment.getCommentType()))
                .map(TicketCommentEntity::getContent)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .toList();
        if (!handlingNotes.isEmpty()) {
            builder.append("\n\nHandling notes:\n");
            for (String note : handlingNotes) {
                builder.append("- ").append(trimToLength(note, 280)).append("\n");
            }
        }
        if (StringUtils.hasText(ticket.getContent())) {
            builder.append("\nOriginal issue context:\n")
                    .append(trimToLength(ticket.getContent().trim(), 1200));
        }
        return trimToLength(builder.toString().trim(), 3000);
    }

    private String resolveSuggestedTitle(TicketEntity ticket, String questionText) {
        String base = StringUtils.hasText(ticket.getTitle()) ? ticket.getTitle().trim() : questionText;
        return trimToLength(base, 120);
    }

    private List<String> buildSuggestionReasons(TicketEntity ticket,
                                                QaMessageEntity qaMessage,
                                                List<TicketCommentEntity> comments,
                                                KnowledgeBaseEntity knowledgeBase,
                                                KnowledgeDraftEntity existingDraft) {
        List<String> reasons = new ArrayList<>();
        reasons.add("工单已处于 " + ticket.getStatus() + " 状态，满足知识回流候选条件。");
        if (qaMessage != null && StringUtils.hasText(qaMessage.getQuestionText())) {
            reasons.add("已复用来源问答的问题文本，能保持知识条目和用户真实提问一致。");
        }
        boolean hasSolution = comments.stream().anyMatch(comment -> COMMENT_SOLUTION.equals(comment.getCommentType()));
        reasons.add(hasSolution ? "处理记录中存在解决方案评论，可作为草稿答案主体。" : "未找到解决方案评论，建议人工补充答案后再发布。");
        if (knowledgeBase != null) {
            reasons.add("推荐写入知识库：" + knowledgeBase.getKbName() + "。");
        } else {
            reasons.add("当前没有可自动推荐的启用知识库，需要人工选择目标知识库。");
        }
        if (existingDraft != null) {
            reasons.add("该工单已有关联知识草稿，建议继续审核已有草稿，避免重复沉淀。");
        }
        return reasons;
    }

    private double resolveSuggestionConfidence(TicketEntity ticket,
                                               QaMessageEntity qaMessage,
                                               List<TicketCommentEntity> comments,
                                               KnowledgeBaseEntity knowledgeBase,
                                               KnowledgeDraftEntity existingDraft) {
        double score = 0.45;
        if (RESOLVED_TICKET_STATUSES.contains(ticket.getStatus())) {
            score += 0.15;
        }
        if (qaMessage != null && StringUtils.hasText(qaMessage.getQuestionText())) {
            score += 0.12;
        }
        if (comments.stream().anyMatch(comment -> COMMENT_SOLUTION.equals(comment.getCommentType()))) {
            score += 0.18;
        }
        if (knowledgeBase != null) {
            score += 0.08;
        }
        if (existingDraft != null) {
            score -= 0.18;
        }
        return Math.max(0.1, Math.min(0.98, Math.round(score * 100.0) / 100.0));
    }

    private Long findTicketIdByNo(String ticketNo) {
        if (!StringUtils.hasText(ticketNo)) {
            return null;
        }
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<TicketEntity>()
                .eq(TicketEntity::getTenantId, currentUser.tenantId())
                .eq(TicketEntity::getTicketNo, ticketNo.trim());
        if (!isAdminUser()) {
            wrapper.eq(TicketEntity::getReporterUserId, currentUser.userId());
        }
        TicketEntity ticket = ticketMapper.selectOne(wrapper.last("limit 1"));
        return ticket == null ? null : ticket.getId();
    }

    private void ensureAdminToolAllowed() {
        if (!isAdminUser()) {
            throw new BizException(ErrorCode.FORBIDDEN, "Current user is not allowed to execute this agent tool");
        }
    }

    private boolean isAdminUser() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return currentUser.roleCodes() != null && currentUser.roleCodes().stream().anyMatch(ADMIN_ROLES::contains);
    }

    private AgentToolDefinitionVO tool(String code, String name, String description, String requiredRole, List<String> argumentHints) {
        return AgentToolDefinitionVO.builder()
                .toolCode(code)
                .toolName(name)
                .description(description)
                .requiredRole(requiredRole)
                .argumentHints(argumentHints)
                .build();
    }

    private String normalizeToolCode(String toolCode) {
        return toolCode == null ? "" : toolCode.trim().toUpperCase();
    }

    private String resolveToolName(String toolCode) {
        return listTools().stream()
                .filter(tool -> toolCode.equals(tool.getToolCode()))
                .map(AgentToolDefinitionVO::getToolName)
                .findFirst()
                .orElse(toolCode);
    }

    private String resolveSummary(String toolCode, Object result) {
        if (result instanceof TicketVO ticket) {
            return "工具执行成功，工单 " + ticket.getTicketNo() + " 当前状态为 " + ticket.getStatus() + "。";
        }
        if (result instanceof AgentKnowledgeDraftSuggestionVO suggestion) {
            return "已生成知识草稿建议，推荐标题：" + suggestion.getTitle() + "。";
        }
        return "工具执行成功：" + resolveToolName(toolCode);
    }

    private Long longArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        Long value = longArg(arguments, key);
        return value == null ? defaultValue : value.intValue();
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String trimToLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
