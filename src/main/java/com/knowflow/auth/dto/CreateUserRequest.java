package com.knowflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "username cannot be empty")
    private String username;

    @NotBlank(message = "realName cannot be empty")
    private String realName;

    private String password;
    private String email;
    private String phone;

    @NotEmpty(message = "roleIds cannot be empty")
    private List<Long> roleIds;
}

