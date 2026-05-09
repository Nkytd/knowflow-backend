package com.knowflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String realName;

    @Min(value = 0, message = "age must be greater than or equal to 0")
    @Max(value = 150, message = "age must be less than or equal to 150")
    private Integer age;

    private String gender;

    @Email(message = "email format is invalid")
    private String email;

    private String phone;
}