package com.knowflow.audit.service;

import com.knowflow.audit.entity.AuditLogEntity;
import com.knowflow.audit.vo.AuditLogVO;
import com.knowflow.common.response.PageResponse;

public interface AuditLogService {

    void record(AuditLogEntity entity);

    PageResponse<AuditLogVO> page(Integer pageNo,
                                  Integer pageSize,
                                  String keyword,
                                  String moduleCode,
                                  String actionCode,
                                  String bizType,
                                  Boolean successFlag);

    AuditLogVO getById(Long id);
}
