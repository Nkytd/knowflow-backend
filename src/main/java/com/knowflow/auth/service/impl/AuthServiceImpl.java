package com.knowflow.auth.service.impl;

import com.knowflow.auth.dto.ChangePasswordRequest;
import com.knowflow.auth.dto.LoginRequest;
import com.knowflow.auth.dto.UpdateProfileRequest;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.auth.security.AuthUserPrincipal;
import com.knowflow.auth.security.JwtTokenProvider;
import com.knowflow.auth.service.AuthService;
import com.knowflow.auth.vo.LoginUserVO;
import com.knowflow.auth.vo.MenuItemVO;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final AuthUserDetailsService authUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CurrentUserProvider currentUserProvider;

    public AuthServiceImpl(UserAccountMapper userAccountMapper,
                           AuthUserDetailsService authUserDetailsService,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           CurrentUserProvider currentUserProvider) {
        this.userAccountMapper = userAccountMapper;
        this.authUserDetailsService = authUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public LoginUserVO login(LoginRequest request) {
        AuthUserPrincipal principal = authUserDetailsService.loadUserByUsername(request.getUsername());
        if (!principal.isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), principal.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }

        UserAccountEntity entity = getCurrentUserEntity(principal.getUserId());
        entity.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(entity);

        return toLoginUserVO(entity, principal.getRoleCodes(), jwtTokenProvider.generateToken(principal));
    }

    @Override
    public LoginUserVO currentUser() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UserAccountEntity user = getCurrentUserEntity(currentUser.userId());
        return toLoginUserVO(user, currentUser.roleCodes(), null);
    }

    @Override
    @Transactional
    public LoginUserVO updateProfile(UpdateProfileRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UserAccountEntity user = getCurrentUserEntity(currentUser.userId());
        user.setRealName(normalizeNullableText(request.getRealName(), user.getRealName()));
        user.setAge(request.getAge());
        user.setGender(normalizeNullableText(request.getGender(), null));
        user.setEmail(normalizeNullableText(request.getEmail(), null));
        user.setPhone(normalizeNullableText(request.getPhone(), null));
        userAccountMapper.updateById(user);
        return toLoginUserVO(user, currentUser.roleCodes(), null);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UserAccountEntity user = getCurrentUserEntity(currentUser.userId());
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Old password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userAccountMapper.updateById(user);
    }

    @Override
    public List<MenuItemVO> currentMenus() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        List<MenuItemVO> menus = new ArrayList<>();
        if (currentUser.roleCodes().contains("SUPER_ADMIN")) {
            menus.add(MenuItemVO.builder().name("租户管理").path("/platform/tenants").build());
        }
        if (currentUser.roleCodes().contains("TENANT_ADMIN")
                || currentUser.roleCodes().contains("KNOWLEDGE_OPERATOR")
                || currentUser.roleCodes().contains("SUPPORT_AGENT")) {
            menus.add(MenuItemVO.builder().name("运营看板").path("/admin/dashboard").build());
            menus.add(MenuItemVO.builder().name("问答记录").path("/admin/qa-records").build());
            menus.add(MenuItemVO.builder().name("工单管理").path("/admin/tickets").build());
            menus.add(MenuItemVO.builder().name("审计日志").path("/admin/audit-logs").build());
        }
        if (currentUser.roleCodes().contains("TENANT_ADMIN")
                || currentUser.roleCodes().contains("KNOWLEDGE_OPERATOR")) {
            menus.add(MenuItemVO.builder().name("知识运营").path("/admin/knowledge-bases").build());
            menus.add(MenuItemVO.builder().name("文档管理").path("/admin/documents").build());
            menus.add(MenuItemVO.builder().name("解析任务").path("/admin/parse-tasks").build());
            menus.add(MenuItemVO.builder().name("检索评估").path("/admin/retrieval-evaluations").build());
            menus.add(MenuItemVO.builder().name("运维健康").path("/admin/ops-health").build());
            menus.add(MenuItemVO.builder().name("死信治理").path("/admin/dead-letters").build());
            menus.add(MenuItemVO.builder().name("知识草稿").path("/admin/knowledge-drafts").build());
        }
        if (currentUser.roleCodes().contains("SUPPORT_AGENT")) {
            menus.add(MenuItemVO.builder().name("知识草稿").path("/admin/knowledge-drafts").build());
        }
        if (currentUser.roleCodes().contains("TENANT_ADMIN")) {
            menus.add(MenuItemVO.builder().name("用户管理").path("/admin/users").build());
            menus.add(MenuItemVO.builder().name("角色权限").path("/admin/roles").build());
        }
        return menus;
    }

    private UserAccountEntity getCurrentUserEntity(Long userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User not found");
        }
        return user;
    }

    private LoginUserVO toLoginUserVO(UserAccountEntity user, List<String> roleCodes, String token) {
        return LoginUserVO.builder()
                .token(token)
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .age(user.getAge())
                .gender(user.getGender())
                .status(user.getStatus())
                .roleCodes(roleCodes)
                .build();
    }

    private String normalizeNullableText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
