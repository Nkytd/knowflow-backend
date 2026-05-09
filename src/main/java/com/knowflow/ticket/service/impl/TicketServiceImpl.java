package com.knowflow.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.auth.entity.RoleEntity;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.entity.UserRoleRelEntity;
import com.knowflow.auth.mapper.RoleMapper;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.auth.mapper.UserRoleRelMapper;
import com.knowflow.backflow.entity.KnowledgeDraftEntity;
import com.knowflow.backflow.mapper.KnowledgeDraftMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.entity.RetrievalRecordEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import com.knowflow.qa.mapper.RetrievalRecordMapper;
import com.knowflow.qa.service.QaSessionService;
import com.knowflow.ticket.dto.AddTicketCommentRequest;
import com.knowflow.ticket.dto.AssignTicketRequest;
import com.knowflow.ticket.dto.CloseTicketRequest;
import com.knowflow.ticket.dto.CreateQaHandoffTicketRequest;
import com.knowflow.ticket.dto.ResolveTicketRequest;
import com.knowflow.ticket.dto.UserTicketReplyRequest;
import com.knowflow.ticket.entity.TicketCommentEntity;
import com.knowflow.ticket.entity.TicketEntity;
import com.knowflow.ticket.entity.TicketFlowEntity;
import com.knowflow.ticket.mapper.TicketCommentMapper;
import com.knowflow.ticket.mapper.TicketFlowMapper;
import com.knowflow.ticket.mapper.TicketMapper;
import com.knowflow.ticket.service.TicketService;
import com.knowflow.ticket.vo.TicketAssigneeOptionVO;
import com.knowflow.ticket.vo.TicketCommentVO;
import com.knowflow.ticket.vo.TicketFlowVO;
import com.knowflow.ticket.vo.TicketVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TicketServiceImpl implements TicketService {

    private static final String SOURCE_TYPE_QA_HANDOFF = "QA_HANDOFF";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_WAITING_USER = "WAITING_USER";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final Set<String> OPEN_STATUSES = Set.of(STATUS_PENDING, STATUS_PROCESSING, STATUS_WAITING_USER);
    private static final String USER_STATUS_ENABLED = "ENABLED";
    private static final String CHANNEL_WEB = "WEB";
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    private static final String PRIORITY_HIGH = "HIGH";
    private static final Set<String> PRIORITIES = Set.of("LOW", PRIORITY_MEDIUM, PRIORITY_HIGH, "URGENT");
    private static final String COMMENT_USER_REPLY = "USER_REPLY";
    private static final String COMMENT_AGENT_REPLY = "AGENT_REPLY";
    private static final String COMMENT_INTERNAL_NOTE = "INTERNAL_NOTE";
    private static final String COMMENT_SOLUTION = "SOLUTION";
    private static final Set<String> COMMENT_TYPES = Set.of(COMMENT_USER_REPLY, COMMENT_AGENT_REPLY, COMMENT_INTERNAL_NOTE, COMMENT_SOLUTION);
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_ACCEPT = "ACCEPT";
    private static final String ACTION_ASSIGN = "ASSIGN";
    private static final String ACTION_USER_REPLY = "USER_REPLY";
    private static final String ACTION_AGENT_REPLY = "AGENT_REPLY";
    private static final String ACTION_INTERNAL_NOTE = "INTERNAL_NOTE";
    private static final String ACTION_RESOLVE = "RESOLVE";
    private static final String ACTION_CLOSE = "CLOSE";
    private static final String SLA_ON_TRACK = "ON_TRACK";
    private static final String SLA_AT_RISK = "AT_RISK";
    private static final String SLA_MET = "MET";
    private static final String SLA_BREACHED = "BREACHED";
    private static final String SLA_PAUSED = "PAUSED";
    private static final Duration SLA_AT_RISK_WINDOW = Duration.ofHours(2);
    private static final Set<String> ASSIGNABLE_ROLE_CODES = Set.of("SUPPORT_AGENT", "TENANT_ADMIN");
    private static final Set<String> SLA_STATUSES = Set.of(SLA_ON_TRACK, SLA_AT_RISK, SLA_MET, SLA_BREACHED, SLA_PAUSED);

    private final TicketMapper ticketMapper;
    private final TicketFlowMapper ticketFlowMapper;
    private final TicketCommentMapper ticketCommentMapper;
    private final QaMessageMapper qaMessageMapper;
    private final RetrievalRecordMapper retrievalRecordMapper;
    private final QaSessionService qaSessionService;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final RoleMapper roleMapper;
    private final KnowledgeDraftMapper knowledgeDraftMapper;
    private final CurrentUserProvider currentUserProvider;

    public TicketServiceImpl(TicketMapper ticketMapper,
                             TicketFlowMapper ticketFlowMapper,
                             TicketCommentMapper ticketCommentMapper,
                             QaMessageMapper qaMessageMapper,
                             RetrievalRecordMapper retrievalRecordMapper,
                             QaSessionService qaSessionService,
                             UserAccountMapper userAccountMapper,
                             UserRoleRelMapper userRoleRelMapper,
                             RoleMapper roleMapper,
                             KnowledgeDraftMapper knowledgeDraftMapper,
                             CurrentUserProvider currentUserProvider) {
        this.ticketMapper = ticketMapper;
        this.ticketFlowMapper = ticketFlowMapper;
        this.ticketCommentMapper = ticketCommentMapper;
        this.qaMessageMapper = qaMessageMapper;
        this.retrievalRecordMapper = retrievalRecordMapper;
        this.qaSessionService = qaSessionService;
        this.userAccountMapper = userAccountMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.roleMapper = roleMapper;
        this.knowledgeDraftMapper = knowledgeDraftMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public TicketVO createFromQaMessage(Long qaMessageId, CreateQaHandoffTicketRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        QaMessageEntity qaMessage = getQaMessageForCurrentUser(qaMessageId);

        TicketEntity existing = ticketMapper.selectOne(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, currentUser.tenantId())
                        .eq(TicketEntity::getSourceQaMessageId, qaMessageId)
                        .last("limit 1")
        );
        if (existing != null) {
            return toTicketVO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        TicketEntity entity = new TicketEntity();
        entity.setTenantId(currentUser.tenantId());
        entity.setTicketNo(CodeGenerator.ticketCode());
        entity.setSourceType(SOURCE_TYPE_QA_HANDOFF);
        entity.setSourceQaMessageId(qaMessageId);
        entity.setReporterUserId(currentUser.userId());
        entity.setTitle(resolveHandoffTitle(request, qaMessage));
        entity.setContent(buildHandoffContent(request, qaMessage));
        entity.setPriority(resolvePriority(request == null ? null : request.getPriority(), qaMessage));
        entity.setStatus(STATUS_PENDING);
        entity.setChannel(CHANNEL_WEB);
        entity.setSlaPolicy(resolveSlaPolicy(entity.getPriority()));
        entity.setSlaDueAt(resolveSlaDueAt(entity.getPriority(), now));
        entity.setSlaStatus(SLA_ON_TRACK);
        entity.setLastReplyAt(now);
        ticketMapper.insert(entity);

        createFlow(entity, ACTION_CREATE, null, STATUS_PENDING, "Created from QA handoff");
        return toTicketVO(entity);
    }

    @Override
    public PageResponse<TicketVO> pageMine(Integer pageNo, Integer pageSize, String status) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Page<TicketEntity> page = ticketMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getTenantId, currentUser.tenantId())
                        .eq(TicketEntity::getReporterUserId, currentUser.userId())
                        .eq(StringUtils.hasText(status), TicketEntity::getStatus, status)
                        .orderByDesc(TicketEntity::getUpdatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                buildTicketVOs(page.getRecords())
        );
    }

    @Override
    public TicketVO getMineById(Long id) {
        return toTicketVO(getMyTicket(id));
    }

    @Override
    public List<TicketCommentVO> listMyComments(Long id) {
        TicketEntity ticket = getMyTicket(id);
        return buildCommentVOs(ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketCommentEntity>()
                        .eq(TicketCommentEntity::getTenantId, ticket.getTenantId())
                        .eq(TicketCommentEntity::getTicketId, ticket.getId())
                        .eq(TicketCommentEntity::getVisibleToUser, 1)
                        .orderByAsc(TicketCommentEntity::getCreatedAt)
        ));
    }

    @Override
    @Transactional
    public TicketCommentVO reply(Long id, UserTicketReplyRequest request) {
        TicketEntity ticket = getMyTicket(id);
        if (STATUS_CLOSED.equals(ticket.getStatus()) || STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Current ticket status does not allow user reply");
        }

        String nextStatus = STATUS_WAITING_USER.equals(ticket.getStatus()) ? STATUS_PROCESSING : ticket.getStatus();
        TicketCommentEntity comment = createComment(ticket, COMMENT_USER_REPLY,
                currentUserProvider.getCurrentUser().userId(), request.getContent(), true);

        updateTicketStatus(ticket, nextStatus, null, null);
        createFlow(ticket, ACTION_USER_REPLY, ticket.getStatus(), nextStatus, "User provided additional information");
        ticket.setStatus(nextStatus);
        return buildCommentVOs(List.of(comment)).get(0);
    }

    @Override
    public PageResponse<TicketVO> pageAdmin(Integer pageNo, Integer pageSize, String keyword, String status,
                                            String priority, String sourceType, Long assigneeUserId, String slaStatus) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<TicketEntity>()
                .eq(TicketEntity::getTenantId, tenantId)
                .like(StringUtils.hasText(keyword), TicketEntity::getTitle, keyword)
                .eq(StringUtils.hasText(status), TicketEntity::getStatus, status)
                .eq(StringUtils.hasText(priority), TicketEntity::getPriority, priority)
                .eq(StringUtils.hasText(sourceType), TicketEntity::getSourceType, sourceType)
                .eq(assigneeUserId != null, TicketEntity::getAssigneeUserId, assigneeUserId);
        applySlaStatusFilter(wrapper, slaStatus);
        wrapper.orderByDesc(TicketEntity::getUpdatedAt);

        Page<TicketEntity> page = ticketMapper.selectPage(
                new Page<>(pageNo, pageSize),
                wrapper
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                buildTicketVOs(page.getRecords())
        );
    }

    @Override
    public List<TicketAssigneeOptionVO> listAssignableUsers() {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        List<UserAccountEntity> users = userAccountMapper.selectList(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getTenantId, tenantId)
                        .eq(UserAccountEntity::getStatus, USER_STATUS_ENABLED)
                        .orderByAsc(UserAccountEntity::getRealName)
                        .orderByAsc(UserAccountEntity::getUsername)
        );
        if (users.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = users.stream().map(UserAccountEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(
                new LambdaQueryWrapper<UserRoleRelEntity>()
                        .in(UserRoleRelEntity::getUserId, userIds)
        );
        if (relations.isEmpty()) {
            return List.of();
        }

        Map<Long, RoleEntity> roleMap = roleMapper.selectBatchIds(relations.stream()
                        .map(UserRoleRelEntity::getRoleId)
                        .collect(Collectors.toCollection(LinkedHashSet::new))).stream()
                .collect(Collectors.toMap(RoleEntity::getId, role -> role));

        Map<Long, List<String>> roleCodesByUserId = relations.stream()
                .collect(Collectors.groupingBy(
                        UserRoleRelEntity::getUserId,
                        Collectors.mapping(relation -> {
                            RoleEntity role = roleMap.get(relation.getRoleId());
                            return role == null ? null : role.getRoleCode();
                        }, Collectors.toList())
                ));

        return users.stream()
                .map(user -> {
                    List<String> roleCodes = roleCodesByUserId.getOrDefault(user.getId(), List.of()).stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    return TicketAssigneeOptionVO.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .realName(user.getRealName())
                            .roleCodes(roleCodes)
                            .build();
                })
                .filter(option -> option.getRoleCodes().stream().anyMatch(ASSIGNABLE_ROLE_CODES::contains))
                .toList();
    }

    @Override
    public TicketVO getAdminById(Long id) {
        return toTicketVO(getTicketForCurrentTenant(id));
    }

    @Override
    public List<TicketCommentVO> listAdminComments(Long id) {
        TicketEntity ticket = getTicketForCurrentTenant(id);
        return buildCommentVOs(ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketCommentEntity>()
                        .eq(TicketCommentEntity::getTenantId, ticket.getTenantId())
                        .eq(TicketCommentEntity::getTicketId, ticket.getId())
                        .orderByAsc(TicketCommentEntity::getCreatedAt)
        ));
    }

    @Override
    public List<TicketFlowVO> listAdminFlows(Long id) {
        TicketEntity ticket = getTicketForCurrentTenant(id);
        List<TicketFlowEntity> flows = ticketFlowMapper.selectList(
                new LambdaQueryWrapper<TicketFlowEntity>()
                        .eq(TicketFlowEntity::getTenantId, ticket.getTenantId())
                        .eq(TicketFlowEntity::getTicketId, ticket.getId())
                        .orderByAsc(TicketFlowEntity::getCreatedAt)
        );
        return buildFlowVOs(flows);
    }

    @Override
    @Transactional
    public TicketVO accept(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = getTicketForCurrentTenant(id);
        if (!STATUS_PENDING.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only pending tickets can be accepted");
        }

        String fromStatus = ticket.getStatus();
        ticket.setAssigneeUserId(currentUser.userId());
        updateTicketStatus(ticket, STATUS_PROCESSING, null, null);
        createFlow(ticket, ACTION_ACCEPT, fromStatus, STATUS_PROCESSING, "Ticket accepted by current agent");
        ticket.setStatus(STATUS_PROCESSING);
        return toTicketVO(ticket);
    }

    @Override
    @Transactional
    public TicketVO assign(Long id, AssignTicketRequest request) {
        TicketEntity ticket = getTicketForCurrentTenant(id);
        validateAssignee(request.getAssigneeUserId());

        String fromStatus = ticket.getStatus();
        String nextStatus = STATUS_PENDING.equals(ticket.getStatus()) ? STATUS_PROCESSING : ticket.getStatus();
        ticket.setAssigneeUserId(request.getAssigneeUserId());
        updateTicketStatus(ticket, nextStatus, null, null);
        createFlow(ticket, ACTION_ASSIGN, fromStatus, nextStatus,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "Ticket reassigned");
        ticket.setStatus(nextStatus);
        return toTicketVO(ticket);
    }

    @Override
    @Transactional
    public TicketCommentVO comment(Long id, AddTicketCommentRequest request) {
        TicketEntity ticket = getTicketForCurrentTenant(id);
        if (STATUS_CLOSED.equals(ticket.getStatus()) || STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Current ticket status does not allow new comments");
        }

        String commentType = resolveCommentType(request.getCommentType());
        boolean visibleToUser = resolveVisibleToUser(commentType, request.getVisibleToUser());
        TicketCommentEntity comment = createComment(ticket, commentType,
                currentUserProvider.getCurrentUser().userId(), request.getContent(), visibleToUser);

        String fromStatus = ticket.getStatus();
        String nextStatus = (COMMENT_AGENT_REPLY.equals(commentType) && visibleToUser) ? STATUS_WAITING_USER : fromStatus;
        updateTicketStatus(ticket, nextStatus, null, null);
        createFlow(ticket, toAction(commentType), fromStatus, nextStatus, summarizeRemark(request.getContent()));
        ticket.setStatus(nextStatus);
        return buildCommentVOs(List.of(comment)).get(0);
    }

    @Override
    @Transactional
    public TicketVO resolve(Long id, ResolveTicketRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = getTicketForCurrentTenant(id);
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Closed ticket cannot be resolved again");
        }

        if (ticket.getAssigneeUserId() == null) {
            ticket.setAssigneeUserId(currentUser.userId());
        }
        createComment(ticket, COMMENT_SOLUTION, currentUser.userId(), request.getSolution(), true);
        String fromStatus = ticket.getStatus();
        LocalDateTime now = LocalDateTime.now();
        ticket.setResolvedAt(now);
        updateTicketStatus(ticket, STATUS_RESOLVED, now, null);
        createFlow(ticket, ACTION_RESOLVE, fromStatus, STATUS_RESOLVED, summarizeRemark(request.getSolution()));
        ticket.setStatus(STATUS_RESOLVED);
        return toTicketVO(ticket);
    }

    @Override
    @Transactional
    public TicketVO close(Long id, CloseTicketRequest request) {
        TicketEntity ticket = getTicketForCurrentTenant(id);
        if (!STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Only resolved tickets can be closed");
        }

        String fromStatus = ticket.getStatus();
        LocalDateTime now = LocalDateTime.now();
        ticket.setClosedAt(now);
        updateTicketStatus(ticket, STATUS_CLOSED, ticket.getResolvedAt(), now);
        createFlow(ticket, ACTION_CLOSE, fromStatus, STATUS_CLOSED,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "Ticket closed");
        ticket.setStatus(STATUS_CLOSED);
        return toTicketVO(ticket);
    }

    private TicketEntity getMyTicket(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        TicketEntity ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getId, id)
                        .eq(TicketEntity::getTenantId, currentUser.tenantId())
                        .eq(TicketEntity::getReporterUserId, currentUser.userId())
                        .last("limit 1")
        );
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Ticket not found");
        }
        return ticket;
    }

    private TicketEntity getTicketForCurrentTenant(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        TicketEntity ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getId, id)
                        .eq(TicketEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Ticket not found");
        }
        return ticket;
    }

    private QaMessageEntity getQaMessageForCurrentUser(Long qaMessageId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        QaMessageEntity qaMessage = qaMessageMapper.selectById(qaMessageId);
        if (qaMessage == null || !currentUser.tenantId().equals(qaMessage.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "QA message not found");
        }
        qaSessionService.getEntityByIdForCurrentUser(qaMessage.getSessionId());
        return qaMessage;
    }

    private void validateAssignee(Long assigneeUserId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UserAccountEntity user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getId, assigneeUserId)
                        .eq(UserAccountEntity::getTenantId, currentUser.tenantId())
                        .eq(UserAccountEntity::getStatus, USER_STATUS_ENABLED)
                        .last("limit 1")
        );
        if (user == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Assignee user not found");
        }

        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(
                new LambdaQueryWrapper<UserRoleRelEntity>()
                        .eq(UserRoleRelEntity::getUserId, assigneeUserId)
        );
        if (relations.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Assignee user has no active role");
        }
        List<RoleEntity> roles = roleMapper.selectBatchIds(relations.stream().map(UserRoleRelEntity::getRoleId).toList());
        boolean valid = roles.stream()
                .map(RoleEntity::getRoleCode)
                .anyMatch(ASSIGNABLE_ROLE_CODES::contains);
        if (!valid) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Assignee must be a support agent or tenant admin");
        }
    }

    private void updateTicketStatus(TicketEntity ticket, String nextStatus, LocalDateTime resolvedAt, LocalDateTime closedAt) {
        TicketEntity update = new TicketEntity();
        update.setId(ticket.getId());
        update.setAssigneeUserId(ticket.getAssigneeUserId());
        update.setStatus(nextStatus);
        update.setLastReplyAt(LocalDateTime.now());
        update.setResolvedAt(resolvedAt == null ? ticket.getResolvedAt() : resolvedAt);
        update.setClosedAt(closedAt == null ? ticket.getClosedAt() : closedAt);
        applySlaFinalState(ticket, update, nextStatus);
        ticketMapper.updateById(update);
        ticket.setUpdatedAt(update.getUpdatedAt());
        ticket.setLastReplyAt(update.getLastReplyAt());
        ticket.setResolvedAt(update.getResolvedAt());
        ticket.setClosedAt(update.getClosedAt());
        if (update.getSlaStatus() != null) {
            ticket.setSlaStatus(update.getSlaStatus());
        }
        if (update.getSlaBreachedAt() != null) {
            ticket.setSlaBreachedAt(update.getSlaBreachedAt());
        }
    }

    private TicketCommentEntity createComment(TicketEntity ticket, String commentType, Long userId,
                                              String content, boolean visibleToUser) {
        TicketCommentEntity entity = new TicketCommentEntity();
        entity.setTenantId(ticket.getTenantId());
        entity.setTicketId(ticket.getId());
        entity.setCommentType(commentType);
        entity.setCommentUserId(userId);
        entity.setContent(content.trim());
        entity.setVisibleToUser(visibleToUser ? 1 : 0);
        ticketCommentMapper.insert(entity);
        return entity;
    }

    private void createFlow(TicketEntity ticket, String actionType, String fromStatus, String toStatus, String remark) {
        TicketFlowEntity entity = new TicketFlowEntity();
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        entity.setTenantId(ticket.getTenantId());
        entity.setTicketId(ticket.getId());
        entity.setActionType(actionType);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setOperatorUserId(currentUser.userId());
        entity.setRemark(trimToLength(remark, 1000));
        ticketFlowMapper.insert(entity);
    }

    private List<TicketVO> buildTicketVOs(List<TicketEntity> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> qaMessageIds = new LinkedHashSet<>();
        Set<Long> ticketIds = new LinkedHashSet<>();
        for (TicketEntity ticket : tickets) {
            ticketIds.add(ticket.getId());
            if (ticket.getReporterUserId() != null) {
                userIds.add(ticket.getReporterUserId());
            }
            if (ticket.getAssigneeUserId() != null) {
                userIds.add(ticket.getAssigneeUserId());
            }
            if (ticket.getSourceQaMessageId() != null) {
                qaMessageIds.add(ticket.getSourceQaMessageId());
            }
        }

        Map<Long, UserAccountEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, user -> user));

        Map<Long, QaMessageEntity> qaMessageMap = qaMessageIds.isEmpty()
                ? Collections.emptyMap()
                : qaMessageMapper.selectBatchIds(qaMessageIds).stream()
                .collect(Collectors.toMap(QaMessageEntity::getId, qaMessage -> qaMessage));
        Map<Long, KnowledgeDraftEntity> draftMap = ticketIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeDraftMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDraftEntity>()
                                .in(KnowledgeDraftEntity::getSourceTicketId, ticketIds)
                ).stream().collect(Collectors.toMap(
                        KnowledgeDraftEntity::getSourceTicketId,
                        draft -> draft,
                        (left, right) -> left
                ));

        List<TicketVO> result = new ArrayList<>(tickets.size());
        for (TicketEntity ticket : tickets) {
            result.add(toTicketVO(ticket, userMap, qaMessageMap, draftMap));
        }
        return result;
    }

    private TicketVO toTicketVO(TicketEntity ticket) {
        return buildTicketVOs(List.of(ticket)).get(0);
    }

    private TicketVO toTicketVO(TicketEntity ticket,
                                Map<Long, UserAccountEntity> userMap,
                                Map<Long, QaMessageEntity> qaMessageMap,
                                Map<Long, KnowledgeDraftEntity> draftMap) {
        UserAccountEntity reporter = userMap.get(ticket.getReporterUserId());
        UserAccountEntity assignee = userMap.get(ticket.getAssigneeUserId());
        QaMessageEntity qaMessage = qaMessageMap.get(ticket.getSourceQaMessageId());
        KnowledgeDraftEntity draft = draftMap.get(ticket.getId());
        return TicketVO.builder()
                .id(ticket.getId())
                .ticketNo(ticket.getTicketNo())
                .sourceType(ticket.getSourceType())
                .sourceQaMessageId(ticket.getSourceQaMessageId())
                .reporterUserId(ticket.getReporterUserId())
                .reporterName(reporter == null ? null : reporter.getRealName())
                .assigneeUserId(ticket.getAssigneeUserId())
                .assigneeName(assignee == null ? null : assignee.getRealName())
                .title(ticket.getTitle())
                .content(ticket.getContent())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .channel(ticket.getChannel())
                .slaPolicy(ticket.getSlaPolicy())
                .slaDueAt(ticket.getSlaDueAt())
                .slaStatus(resolveSlaStatusForView(ticket))
                .slaBreachedAt(ticket.getSlaBreachedAt())
                .slaReminderSentAt(ticket.getSlaReminderSentAt())
                .slaBreached(isSlaBreached(ticket))
                .slaRemainingMinutes(resolveSlaRemainingMinutes(ticket))
                .sourceQuestionText(qaMessage == null ? null : qaMessage.getQuestionText())
                .sourceAnswerText(qaMessage == null ? null : qaMessage.getAnswerText())
                .relatedDraftId(draft == null ? null : draft.getId())
                .relatedDraftStatus(draft == null ? null : draft.getStatus())
                .relatedDraftTitle(draft == null ? null : draft.getTitle())
                .lastReplyAt(ticket.getLastReplyAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private List<TicketCommentVO> buildCommentVOs(List<TicketCommentEntity> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = comments.stream()
                .map(TicketCommentEntity::getCommentUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserAccountEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, user -> user));

        return comments.stream()
                .map(comment -> {
                    UserAccountEntity user = userMap.get(comment.getCommentUserId());
                    return TicketCommentVO.builder()
                            .id(comment.getId())
                            .ticketId(comment.getTicketId())
                            .commentType(comment.getCommentType())
                            .commentUserId(comment.getCommentUserId())
                            .commentUserName(user == null ? null : user.getRealName())
                            .content(comment.getContent())
                            .visibleToUser(comment.getVisibleToUser() != null && comment.getVisibleToUser() == 1)
                            .createdAt(comment.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private List<TicketFlowVO> buildFlowVOs(List<TicketFlowEntity> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = flows.stream()
                .map(TicketFlowEntity::getOperatorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserAccountEntity> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, user -> user));

        return flows.stream()
                .map(flow -> {
                    UserAccountEntity user = userMap.get(flow.getOperatorUserId());
                    return TicketFlowVO.builder()
                            .id(flow.getId())
                            .ticketId(flow.getTicketId())
                            .actionType(flow.getActionType())
                            .fromStatus(flow.getFromStatus())
                            .toStatus(flow.getToStatus())
                            .operatorUserId(flow.getOperatorUserId())
                            .operatorUserName(user == null ? null : user.getRealName())
                            .remark(flow.getRemark())
                            .createdAt(flow.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private String resolveHandoffTitle(CreateQaHandoffTicketRequest request, QaMessageEntity qaMessage) {
        if (request != null && StringUtils.hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        String question = normalizeSingleLine(qaMessage.getQuestionText());
        if (!StringUtils.hasText(question)) {
            return "Knowledge question needs manual support";
        }
        return trimToLength(question, 120);
    }

    private String buildHandoffContent(CreateQaHandoffTicketRequest request, QaMessageEntity qaMessage) {
        List<RetrievalRecordEntity> sources = retrievalRecordMapper.selectList(
                new LambdaQueryWrapper<RetrievalRecordEntity>()
                        .eq(RetrievalRecordEntity::getQaMessageId, qaMessage.getId())
                        .orderByAsc(RetrievalRecordEntity::getRankNo)
        );

        StringBuilder builder = new StringBuilder();
        builder.append("Source: QA handoff\n");
        builder.append("Question: ").append(defaultText(qaMessage.getQuestionText())).append("\n\n");
        builder.append("AI Answer: ").append(defaultText(qaMessage.getAnswerText())).append("\n");
        if (!sources.isEmpty()) {
            builder.append("\nRetrieved Sources:\n");
            for (int i = 0; i < Math.min(3, sources.size()); i++) {
                RetrievalRecordEntity source = sources.get(i);
                builder.append(i + 1)
                        .append(". ")
                        .append(defaultText(source.getDocumentName()))
                        .append(" - ")
                        .append(defaultText(source.getSnippetText()))
                        .append("\n");
            }
        }
        if (request != null && StringUtils.hasText(request.getContent())) {
            builder.append("\nUser Supplement:\n").append(request.getContent().trim());
        }
        return builder.toString().trim();
    }

    private String resolvePriority(String priority, QaMessageEntity qaMessage) {
        if (!StringUtils.hasText(priority)) {
            return qaMessage.getNeedHumanHandoff() != null && qaMessage.getNeedHumanHandoff() == 1
                    ? PRIORITY_HIGH
                    : PRIORITY_MEDIUM;
        }
        String normalized = priority.trim().toUpperCase();
        if (!PRIORITIES.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported priority");
        }
        return normalized;
    }

    private String resolveSlaPolicy(String priority) {
        return switch (defaultText(priority).toUpperCase()) {
            case "URGENT" -> "P1-4H";
            case PRIORITY_HIGH -> "P2-8H";
            case PRIORITY_MEDIUM -> "P3-24H";
            case "LOW" -> "P4-72H";
            default -> "P3-24H";
        };
    }

    private LocalDateTime resolveSlaDueAt(String priority, LocalDateTime baseTime) {
        return switch (defaultText(priority).toUpperCase()) {
            case "URGENT" -> baseTime.plusHours(4);
            case PRIORITY_HIGH -> baseTime.plusHours(8);
            case PRIORITY_MEDIUM -> baseTime.plusHours(24);
            case "LOW" -> baseTime.plusHours(72);
            default -> baseTime.plusHours(24);
        };
    }

    private void applySlaStatusFilter(LambdaQueryWrapper<TicketEntity> wrapper, String slaStatus) {
        if (!StringUtils.hasText(slaStatus)) {
            return;
        }

        String normalized = slaStatus.trim().toUpperCase();
        if (!SLA_STATUSES.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported SLA status");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime riskCutoff = now.plus(SLA_AT_RISK_WINDOW);
        switch (normalized) {
            case SLA_AT_RISK -> wrapper
                    .in(TicketEntity::getStatus, OPEN_STATUSES)
                    .isNotNull(TicketEntity::getSlaDueAt)
                    .gt(TicketEntity::getSlaDueAt, now)
                    .le(TicketEntity::getSlaDueAt, riskCutoff)
                    .ne(TicketEntity::getSlaStatus, SLA_BREACHED)
                    .ne(TicketEntity::getSlaStatus, SLA_PAUSED);
            case SLA_BREACHED -> wrapper.and(query -> query
                    .eq(TicketEntity::getSlaStatus, SLA_BREACHED)
                    .or(overdue -> overdue
                            .in(TicketEntity::getStatus, OPEN_STATUSES)
                            .isNotNull(TicketEntity::getSlaDueAt)
                            .le(TicketEntity::getSlaDueAt, now)));
            case SLA_ON_TRACK -> wrapper
                    .in(TicketEntity::getStatus, OPEN_STATUSES)
                    .eq(TicketEntity::getSlaStatus, SLA_ON_TRACK)
                    .and(query -> query
                            .isNull(TicketEntity::getSlaDueAt)
                            .or(notRiskYet -> notRiskYet.gt(TicketEntity::getSlaDueAt, riskCutoff)));
            default -> wrapper.eq(TicketEntity::getSlaStatus, normalized);
        }
    }

    private void applySlaFinalState(TicketEntity ticket, TicketEntity update, String nextStatus) {
        if (!STATUS_RESOLVED.equals(nextStatus) && !STATUS_CLOSED.equals(nextStatus)) {
            return;
        }
        if (SLA_BREACHED.equals(ticket.getSlaStatus())) {
            update.setSlaStatus(SLA_BREACHED);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (ticket.getSlaDueAt() != null && now.isAfter(ticket.getSlaDueAt())) {
            update.setSlaStatus(SLA_BREACHED);
            update.setSlaBreachedAt(ticket.getSlaBreachedAt() == null ? now : ticket.getSlaBreachedAt());
            return;
        }
        update.setSlaStatus(SLA_MET);
    }

    private String resolveSlaStatusForView(TicketEntity ticket) {
        String storedStatus = ticket.getSlaStatus() == null ? SLA_ON_TRACK : ticket.getSlaStatus();
        if (SLA_BREACHED.equals(storedStatus)) {
            return SLA_BREACHED;
        }
        if (SLA_PAUSED.equals(storedStatus)) {
            return SLA_PAUSED;
        }

        if (STATUS_RESOLVED.equals(ticket.getStatus()) || STATUS_CLOSED.equals(ticket.getStatus())) {
            if (SLA_MET.equals(storedStatus)) {
                return SLA_MET;
            }
            LocalDateTime finalAt = ticket.getResolvedAt() == null ? ticket.getClosedAt() : ticket.getResolvedAt();
            if (ticket.getSlaDueAt() != null && finalAt != null && finalAt.isAfter(ticket.getSlaDueAt())) {
                return SLA_BREACHED;
            }
            return SLA_MET;
        }

        if (ticket.getSlaDueAt() != null && OPEN_STATUSES.contains(ticket.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            if (!now.isBefore(ticket.getSlaDueAt())) {
                return SLA_BREACHED;
            }
            if (!ticket.getSlaDueAt().isAfter(now.plus(SLA_AT_RISK_WINDOW))) {
                return SLA_AT_RISK;
            }
        }
        if (!SLA_ON_TRACK.equals(storedStatus)) {
            return storedStatus;
        }
        return SLA_ON_TRACK;
    }

    private boolean isSlaBreached(TicketEntity ticket) {
        return SLA_BREACHED.equals(resolveSlaStatusForView(ticket));
    }

    private Long resolveSlaRemainingMinutes(TicketEntity ticket) {
        if (ticket.getSlaDueAt() == null || !OPEN_STATUSES.contains(ticket.getStatus())) {
            return null;
        }
        return Duration.between(LocalDateTime.now(), ticket.getSlaDueAt()).toMinutes();
    }

    private String resolveCommentType(String commentType) {
        String normalized = commentType.trim().toUpperCase();
        if (!COMMENT_TYPES.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported comment type");
        }
        return normalized;
    }

    private boolean resolveVisibleToUser(String commentType, Boolean visibleToUser) {
        if (COMMENT_INTERNAL_NOTE.equals(commentType)) {
            return false;
        }
        return visibleToUser == null || visibleToUser;
    }

    private String toAction(String commentType) {
        return switch (commentType) {
            case COMMENT_AGENT_REPLY -> ACTION_AGENT_REPLY;
            case COMMENT_INTERNAL_NOTE -> ACTION_INTERNAL_NOTE;
            default -> ACTION_AGENT_REPLY;
        };
    }

    private String summarizeRemark(String text) {
        return trimToLength(normalizeSingleLine(text), 200);
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "N/A";
    }

    private String normalizeSingleLine(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String trimToLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
