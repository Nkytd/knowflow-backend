package com.knowflow.ticket.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TicketAssigneeOptionVO {

    private Long userId;
    private String username;
    private String realName;
    private List<String> roleCodes;
}
