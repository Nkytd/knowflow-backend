package com.knowflow.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginUserVO {

    private String token;
    private Long userId;
    private Long tenantId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Integer age;
    private String gender;
    private String status;
    private List<String> roleCodes;
}