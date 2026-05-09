package com.knowflow.parser.deadletter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.parser.deadletter.entity.DeadLetterMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterMessageMapper extends BaseMapper<DeadLetterMessageEntity> {
}
