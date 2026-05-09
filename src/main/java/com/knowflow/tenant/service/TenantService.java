package com.knowflow.tenant.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.tenant.dto.CreateTenantRequest;
import com.knowflow.tenant.dto.UpdateTenantRequest;
import com.knowflow.tenant.vo.TenantVO;

public interface TenantService {

    TenantVO create(CreateTenantRequest request);

    PageResponse<TenantVO> page(Integer pageNo, Integer pageSize, String keyword, String status);

    TenantVO getById(Long id);

    TenantVO update(Long id, UpdateTenantRequest request);

    void updateStatus(Long id, String status);

    void validateTenantExists(Long tenantId);
}

