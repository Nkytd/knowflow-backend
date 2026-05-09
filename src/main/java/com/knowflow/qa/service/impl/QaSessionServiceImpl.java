package com.knowflow.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.qa.dto.CreateQaSessionRequest;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.mapper.QaSessionMapper;
import com.knowflow.qa.service.QaSessionService;
import com.knowflow.qa.vo.QaSessionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class QaSessionServiceImpl implements QaSessionService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final QaSessionMapper qaSessionMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final CurrentUserProvider currentUserProvider;

    public QaSessionServiceImpl(QaSessionMapper qaSessionMapper,
                                KnowledgeBaseService knowledgeBaseService,
                                CurrentUserProvider currentUserProvider) {
        this.qaSessionMapper = qaSessionMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public QaSessionVO create(CreateQaSessionRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        knowledgeBaseService.validateKnowledgeBaseExists(currentUser.tenantId(), request.getKnowledgeBaseId());

        QaSessionEntity entity = new QaSessionEntity();
        entity.setTenantId(currentUser.tenantId());
        entity.setSessionNo(CodeGenerator.prefixedCode("S"));
        entity.setKnowledgeBaseId(request.getKnowledgeBaseId());
        entity.setUserId(currentUser.userId());
        entity.setSessionTitle(request.getSessionTitle());
        entity.setStatus(STATUS_ACTIVE);
        entity.setLastMessageAt(LocalDateTime.now());
        qaSessionMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public PageResponse<QaSessionVO> page(Integer pageNo, Integer pageSize) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Page<QaSessionEntity> page = qaSessionMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<QaSessionEntity>()
                        .eq(QaSessionEntity::getTenantId, currentUser.tenantId())
                        .eq(QaSessionEntity::getUserId, currentUser.userId())
                        .orderByDesc(QaSessionEntity::getLastMessageAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public QaSessionVO getById(Long id) {
        return toVO(getEntityByIdForCurrentUser(id));
    }

    @Override
    public QaSessionEntity getEntityByIdForCurrentUser(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        QaSessionEntity entity = qaSessionMapper.selectOne(
                new LambdaQueryWrapper<QaSessionEntity>()
                        .eq(QaSessionEntity::getId, id)
                        .eq(QaSessionEntity::getTenantId, currentUser.tenantId())
                        .eq(QaSessionEntity::getUserId, currentUser.userId())
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "QA session not found");
        }
        return entity;
    }

    @Override
    @Transactional
    public void touchLastMessageAt(Long sessionId) {
        QaSessionEntity entity = qaSessionMapper.selectById(sessionId);
        if (entity == null) {
            return;
        }
        entity.setLastMessageAt(LocalDateTime.now());
        qaSessionMapper.updateById(entity);
    }

    private QaSessionVO toVO(QaSessionEntity entity) {
        return QaSessionVO.builder()
                .id(entity.getId())
                .sessionNo(entity.getSessionNo())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .sessionTitle(entity.getSessionTitle())
                .status(entity.getStatus())
                .lastMessageAt(entity.getLastMessageAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

