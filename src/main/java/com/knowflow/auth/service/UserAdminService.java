package com.knowflow.auth.service;

import com.knowflow.auth.dto.AssignRolesRequest;
import com.knowflow.auth.dto.CreateUserRequest;
import com.knowflow.auth.dto.ResetPasswordRequest;
import com.knowflow.auth.dto.UpdateUserRequest;
import com.knowflow.auth.vo.UserVO;
import com.knowflow.common.response.PageResponse;

public interface UserAdminService {

    UserVO create(CreateUserRequest request);

    PageResponse<UserVO> page(Integer pageNo, Integer pageSize, String keyword, String status);

    UserVO getById(Long id);

    UserVO update(Long id, UpdateUserRequest request);

    void updateStatus(Long id, String status);

    void resetPassword(Long id, ResetPasswordRequest request);

    void assignRoles(Long id, AssignRolesRequest request);
}

