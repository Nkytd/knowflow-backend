package com.knowflow.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserVO {

    private Long id;
    private Long tenantId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime lastLoginAt;
    private List<String> roleCodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

