package com.knowflow.auth.service;

import com.knowflow.auth.dto.ChangePasswordRequest;
import com.knowflow.auth.dto.LoginRequest;
import com.knowflow.auth.dto.UpdateProfileRequest;
import com.knowflow.auth.vo.LoginUserVO;
import com.knowflow.auth.vo.MenuItemVO;

import java.util.List;

public interface AuthService {

    LoginUserVO login(LoginRequest request);

    LoginUserVO currentUser();

    List<MenuItemVO> currentMenus();

    void changePassword(ChangePasswordRequest request);

    LoginUserVO updateProfile(UpdateProfileRequest request);
}