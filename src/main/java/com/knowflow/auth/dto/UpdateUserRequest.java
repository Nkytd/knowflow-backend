package com.knowflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "realName cannot be empty")
    private String realName;

    private String email;
    private String phone;
}

