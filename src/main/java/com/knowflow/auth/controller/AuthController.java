package com.knowflow.auth.controller;

import com.knowflow.auth.dto.ChangePasswordRequest;
import com.knowflow.auth.dto.LoginRequest;
import com.knowflow.auth.dto.UpdateProfileRequest;
import com.knowflow.auth.service.AuthService;
import com.knowflow.auth.vo.LoginUserVO;
import com.knowflow.auth.vo.MenuItemVO;
import com.knowflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginUserVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<LoginUserVO> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuItemVO>> currentMenus() {
        return ApiResponse.success(authService.currentMenus());
    }

    @PostMapping("/profile")
    public ApiResponse<LoginUserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success();
    }
}