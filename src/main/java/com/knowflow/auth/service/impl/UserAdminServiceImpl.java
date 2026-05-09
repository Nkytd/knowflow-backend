package com.knowflow.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.auth.dto.AssignRolesRequest;
import com.knowflow.auth.dto.CreateUserRequest;
import com.knowflow.auth.dto.ResetPasswordRequest;
import com.knowflow.auth.dto.UpdateUserRequest;
import com.knowflow.auth.entity.RoleEntity;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.entity.UserRoleRelEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.auth.mapper.UserRoleRelMapper;
import com.knowflow.auth.service.RoleService;
import com.knowflow.auth.service.UserAdminService;
import com.knowflow.auth.vo.UserVO;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final String DEFAULT_PASSWORD = "ChangeMe@123";

    private final UserAccountMapper userAccountMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public UserAdminServiceImpl(UserAccountMapper userAccountMapper,
                                UserRoleRelMapper userRoleRelMapper,
                                RoleService roleService,
                                PasswordEncoder passwordEncoder,
                                CurrentUserProvider currentUserProvider) {
        this.userAccountMapper = userAccountMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public UserVO create(CreateUserRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        if (existsByUsername(request.getUsername(), null)) {
            throw new BizException(ErrorCode.CONFLICT, "Username already exists");
        }

        validateAssignableRoles(request.getRoleIds());

        UserAccountEntity user = new UserAccountEntity();
        user.setTenantId(currentUser.tenantId());
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus("ENABLED");
        user.setPasswordHash(passwordEncoder.encode(StringUtils.hasText(request.getPassword()) ? request.getPassword() : DEFAULT_PASSWORD));
        userAccountMapper.insert(user);

        replaceUserRoles(user.getId(), request.getRoleIds());
        return buildUserVO(user);
    }

    @Override
    public PageResponse<UserVO> page(Integer pageNo, Integer pageSize, String keyword, String status) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Page<UserAccountEntity> page = userAccountMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getTenantId, currentUser.tenantId())
                        .like(StringUtils.hasText(keyword), UserAccountEntity::getUsername, keyword)
                        .eq(StringUtils.hasText(status), UserAccountEntity::getStatus, status)
                        .orderByDesc(UserAccountEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::buildUserVO).toList()
        );
    }

    @Override
    public UserVO getById(Long id) {
        return buildUserVO(getEntityById(id));
    }

    @Override
    @Transactional
    public UserVO update(Long id, UpdateUserRequest request) {
        UserAccountEntity user = getEntityById(id);
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        userAccountMapper.updateById(user);
        return buildUserVO(user);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        UserAccountEntity user = getEntityById(id);
        user.setStatus(status);
        userAccountMapper.updateById(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        UserAccountEntity user = getEntityById(id);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userAccountMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, AssignRolesRequest request) {
        getEntityById(id);
        validateAssignableRoles(request.getRoleIds());
        replaceUserRoles(id, request.getRoleIds());
    }

    private void validateAssignableRoles(List<Long> roleIds) {
        List<RoleEntity> roles = roleService.listRolesByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Some role ids are invalid");
        }
        boolean hasPlatformRole = roles.stream().anyMatch(role -> "PLATFORM".equals(role.getScopeType()));
        if (hasPlatformRole) {
            throw new BizException(ErrorCode.FORBIDDEN, "Tenant admin cannot assign platform roles");
        }
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRelEntity>().eq(UserRoleRelEntity::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleRelEntity relation = new UserRoleRelEntity();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleRelMapper.insert(relation);
        }
    }

    private UserAccountEntity getEntityById(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UserAccountEntity user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getId, id)
                        .eq(UserAccountEntity::getTenantId, currentUser.tenantId())
                        .last("limit 1")
        );
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "User not found");
        }
        return user;
    }

    private boolean existsByUsername(String username, Long excludeId) {
        UserAccountEntity user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUsername, username)
                        .ne(excludeId != null, UserAccountEntity::getId, excludeId)
                        .last("limit 1")
        );
        return user != null;
    }

    private UserVO buildUserVO(UserAccountEntity user) {
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(
                new LambdaQueryWrapper<UserRoleRelEntity>().eq(UserRoleRelEntity::getUserId, user.getId())
        );
        List<Long> roleIds = relations.stream().map(UserRoleRelEntity::getRoleId).toList();
        List<RoleEntity> roles = roleService.listRolesByIds(roleIds);
        Map<Long, RoleEntity> roleMap = roles.stream().collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        List<String> roleCodes = roleIds.stream()
                .map(roleMap::get)
                .filter(java.util.Objects::nonNull)
                .map(RoleEntity::getRoleCode)
                .toList();

        return UserVO.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .roleCodes(roleCodes)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

