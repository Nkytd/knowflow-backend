package com.knowflow.auth.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.auth.dto.AssignRolesRequest;
import com.knowflow.auth.dto.CreateUserRequest;
import com.knowflow.auth.dto.ResetPasswordRequest;
import com.knowflow.auth.dto.UpdateUserRequest;
import com.knowflow.auth.dto.UpdateUserStatusRequest;
import com.knowflow.auth.service.UserAdminService;
import com.knowflow.auth.vo.UserVO;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping
    @OperationAudit(moduleCode = "USER", actionCode = "CREATE", bizType = "USER",
            summary = "创建租户用户", bizNoField = "username")
    public ApiResponse<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userAdminService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.success(userAdminService.page(pageNo, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserVO> detail(@PathVariable Long id) {
        return ApiResponse.success(userAdminService.getById(id));
    }

    @PutMapping("/{id}")
    @OperationAudit(moduleCode = "USER", actionCode = "UPDATE", bizType = "USER",
            summary = "更新用户资料", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userAdminService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @OperationAudit(moduleCode = "USER", actionCode = "UPDATE_STATUS", bizType = "USER",
            summary = "更新用户状态", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminService.updateStatus(id, request.getStatus());
        return ApiResponse.success();
    }

    @PutMapping("/{id}/reset-password")
    @OperationAudit(moduleCode = "USER", actionCode = "RESET_PASSWORD", bizType = "USER",
            summary = "重置用户密码", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/roles")
    @OperationAudit(moduleCode = "USER", actionCode = "ASSIGN_ROLES", bizType = "USER",
            summary = "分配用户角色", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                         @Valid @RequestBody AssignRolesRequest request) {
        userAdminService.assignRoles(id, request);
        return ApiResponse.success();
    }
}
