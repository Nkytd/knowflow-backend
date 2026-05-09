package com.knowflow.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.qa.entity.QaMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QaMessageMapper extends BaseMapper<QaMessageEntity> {
}

