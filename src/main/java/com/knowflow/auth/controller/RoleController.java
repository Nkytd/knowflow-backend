package com.knowflow.auth.controller;

import com.knowflow.auth.service.RoleService;
import com.knowflow.auth.vo.RoleVO;
import com.knowflow.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.success(roleService.listAssignableRoles());
    }
}

