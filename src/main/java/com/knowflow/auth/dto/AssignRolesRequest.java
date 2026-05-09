package com.knowflow.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssignRolesRequest {

    @NotEmpty(message = "roleIds cannot be empty")
    private List<Long> roleIds;
}

