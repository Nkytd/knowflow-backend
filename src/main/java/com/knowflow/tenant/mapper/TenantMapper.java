package com.knowflow.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.tenant.entity.TenantEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {
}

