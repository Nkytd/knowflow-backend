package com.knowflow.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.tenant.dto.CreateTenantRequest;
import com.knowflow.tenant.dto.UpdateTenantRequest;
import com.knowflow.tenant.entity.TenantEntity;
import com.knowflow.tenant.mapper.TenantMapper;
import com.knowflow.tenant.service.TenantService;
import com.knowflow.tenant.vo.TenantVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class TenantServiceImpl implements TenantService {

    private static final String STATUS_ENABLED = "ENABLED";

    private final TenantMapper tenantMapper;

    public TenantServiceImpl(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    @Transactional
    public TenantVO create(CreateTenantRequest request) {
        String tenantCode = StringUtils.hasText(request.getTenantCode()) ? request.getTenantCode() : CodeGenerator.tenantCode();
        if (existsByTenantCode(tenantCode, null)) {
            throw new BizException(ErrorCode.CONFLICT, "租户编码已存在");
        }

        TenantEntity entity = new TenantEntity();
        entity.setTenantCode(tenantCode);
        entity.setTenantName(request.getTenantName());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        entity.setStatus(STATUS_ENABLED);
        tenantMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public PageResponse<TenantVO> page(Integer pageNo, Integer pageSize, String keyword, String status) {
        Page<TenantEntity> page = tenantMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<TenantEntity>()
                        .like(StringUtils.hasText(keyword), TenantEntity::getTenantName, keyword)
                        .eq(StringUtils.hasText(status), TenantEntity::getStatus, status)
                        .orderByDesc(TenantEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public TenantVO getById(Long id) {
        return toVO(getEntityById(id));
    }

    @Override
    @Transactional
    public TenantVO update(Long id, UpdateTenantRequest request) {
        TenantEntity entity = getEntityById(id);
        entity.setTenantName(request.getTenantName());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        tenantMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        TenantEntity entity = getEntityById(id);
        entity.setStatus(status);
        tenantMapper.updateById(entity);
    }

    @Override
    public void validateTenantExists(Long tenantId) {
        getEntityById(tenantId);
    }

    private TenantEntity getEntityById(Long id) {
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        return entity;
    }

    private boolean existsByTenantCode(String tenantCode, Long excludeId) {
        TenantEntity entity = tenantMapper.selectOne(
                new LambdaQueryWrapper<TenantEntity>()
                        .eq(TenantEntity::getTenantCode, tenantCode)
                        .ne(Objects.nonNull(excludeId), TenantEntity::getId, excludeId)
                        .last("limit 1")
        );
        return entity != null;
    }

    private TenantVO toVO(TenantEntity entity) {
        return TenantVO.builder()
                .id(entity.getId())
                .tenantCode(entity.getTenantCode())
                .tenantName(entity.getTenantName())
                .status(entity.getStatus())
                .contactName(entity.getContactName())
                .contactPhone(entity.getContactPhone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

