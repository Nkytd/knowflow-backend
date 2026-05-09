package com.knowflow.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String scopeType;
    private String status;
}

