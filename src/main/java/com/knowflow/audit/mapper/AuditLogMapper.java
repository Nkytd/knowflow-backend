package com.knowflow.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.audit.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
