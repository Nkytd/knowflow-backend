package com.knowflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.ticket.entity.TicketEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<TicketEntity> {
}
