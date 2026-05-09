package com.knowflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "oldPassword cannot be empty")
    private String oldPassword;

    @NotBlank(message = "newPassword cannot be empty")
    private String newPassword;
}