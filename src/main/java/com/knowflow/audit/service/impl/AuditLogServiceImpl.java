package com.knowflow.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.audit.entity.AuditLogEntity;
import com.knowflow.audit.mapper.AuditLogMapper;
import com.knowflow.audit.service.AuditLogService;
import com.knowflow.audit.vo.AuditLogVO;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final CurrentUserProvider currentUserProvider;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper, CurrentUserProvider currentUserProvider) {
        this.auditLogMapper = auditLogMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public void record(AuditLogEntity entity) {
        auditLogMapper.insert(entity);
    }

    @Override
    public PageResponse<AuditLogVO> page(Integer pageNo,
                                         Integer pageSize,
                                         String keyword,
                                         String moduleCode,
                                         String actionCode,
                                         String bizType,
                                         Boolean successFlag) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AuditLogEntity>()
                        .eq(AuditLogEntity::getTenantId, tenantId)
                        .eq(StringUtils.hasText(moduleCode), AuditLogEntity::getModuleCode, normalize(moduleCode))
                        .eq(StringUtils.hasText(actionCode), AuditLogEntity::getActionCode, normalize(actionCode))
                        .eq(StringUtils.hasText(bizType), AuditLogEntity::getBizType, normalize(bizType))
                        .eq(successFlag != null, AuditLogEntity::getSuccessFlag, Boolean.TRUE.equals(successFlag) ? 1 : 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(AuditLogEntity::getBizNo, keyword)
                                .or()
                                .like(AuditLogEntity::getOperatorUsername, keyword)
                                .or()
                                .like(AuditLogEntity::getOperatorRealName, keyword)
                                .or()
                                .like(AuditLogEntity::getOperationSummary, keyword)
                                .or()
                                .like(AuditLogEntity::getRequestUri, keyword)
                                .or()
                                .like(AuditLogEntity::getErrorMessage, keyword))
                        .orderByDesc(AuditLogEntity::getCreatedAt)
        );
        return PageResponse.of((int) page.getCurrent(), (int) page.getSize(), page.getTotal(), toVOs(page.getRecords()));
    }

    @Override
    public AuditLogVO getById(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        AuditLogEntity entity = auditLogMapper.selectOne(
                new LambdaQueryWrapper<AuditLogEntity>()
                        .eq(AuditLogEntity::getId, id)
                        .eq(AuditLogEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Audit log not found");
        }
        return toVO(entity);
    }

    private List<AuditLogVO> toVOs(List<AuditLogEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(this::toVO).toList();
    }

    private AuditLogVO toVO(AuditLogEntity entity) {
        return AuditLogVO.builder()
                .id(entity.getId())
                .moduleCode(entity.getModuleCode())
                .actionCode(entity.getActionCode())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId())
                .bizNo(entity.getBizNo())
                .operatorUserId(entity.getOperatorUserId())
                .operatorUsername(entity.getOperatorUsername())
                .operatorRealName(entity.getOperatorRealName())
                .requestMethod(entity.getRequestMethod())
                .requestUri(entity.getRequestUri())
                .operationSummary(entity.getOperationSummary())
                .successFlag(entity.getSuccessFlag() != null && entity.getSuccessFlag() == 1)
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : value;
    }
}
