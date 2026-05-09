package com.knowflow.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.auth.entity.RoleEntity;
import com.knowflow.auth.entity.UserRoleRelEntity;
import com.knowflow.auth.mapper.RoleMapper;
import com.knowflow.auth.mapper.UserRoleRelMapper;
import com.knowflow.auth.service.RoleService;
import com.knowflow.auth.vo.RoleVO;
import com.knowflow.common.security.CurrentUserProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final CurrentUserProvider currentUserProvider;

    public RoleServiceImpl(RoleMapper roleMapper,
                           UserRoleRelMapper userRoleRelMapper,
                           CurrentUserProvider currentUserProvider) {
        this.roleMapper = roleMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public List<RoleVO> listAssignableRoles() {
        boolean isSuperAdmin = currentUserProvider.getCurrentUser().roleCodes().contains("SUPER_ADMIN");
        List<RoleEntity> roles = roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getStatus, "ENABLED")
                        .ne(!isSuperAdmin, RoleEntity::getRoleCode, "SUPER_ADMIN")
                        .orderByAsc(RoleEntity::getId)
        );
        return roles.stream().map(this::toVO).toList();
    }

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        List<UserRoleRelEntity> relations = userRoleRelMapper.selectList(
                new LambdaQueryWrapper<UserRoleRelEntity>()
                        .eq(UserRoleRelEntity::getUserId, userId)
        );
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = relations.stream().map(UserRoleRelEntity::getRoleId).toList();
        return roleMapper.selectList(
                        new LambdaQueryWrapper<RoleEntity>()
                                .in(RoleEntity::getId, roleIds)
                                .eq(RoleEntity::getStatus, "ENABLED")
                ).stream()
                .map(RoleEntity::getRoleCode)
                .toList();
    }

    @Override
    public List<RoleEntity> listRolesByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<RoleEntity> roles = roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, roleIds)
                        .eq(RoleEntity::getStatus, "ENABLED")
        );
        Map<Long, RoleEntity> roleMap = roles.stream().collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        return roleIds.stream().map(roleMap::get).filter(java.util.Objects::nonNull).toList();
    }

    private RoleVO toVO(RoleEntity entity) {
        return RoleVO.builder()
                .id(entity.getId())
                .roleCode(entity.getRoleCode())
                .roleName(entity.getRoleName())
                .scopeType(entity.getScopeType())
                .status(entity.getStatus())
                .build();
    }
}

