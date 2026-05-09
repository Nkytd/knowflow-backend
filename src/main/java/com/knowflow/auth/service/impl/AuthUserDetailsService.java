package com.knowflow.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.auth.entity.UserAccountEntity;
import com.knowflow.auth.mapper.UserAccountMapper;
import com.knowflow.auth.security.AuthUserPrincipal;
import com.knowflow.auth.service.RoleService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final UserAccountMapper userAccountMapper;
    private final RoleService roleService;

    public AuthUserDetailsService(UserAccountMapper userAccountMapper, RoleService roleService) {
        this.userAccountMapper = userAccountMapper;
        this.roleService = roleService;
    }

    @Override
    public AuthUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccountEntity user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUsername, username)
                        .last("limit 1")
        );
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        List<String> roleCodes = roleService.listRoleCodesByUserId(user.getId());
        return new AuthUserPrincipal(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getRealName(),
                user.getPasswordHash(),
                user.getStatus(),
                roleCodes
        );
    }
}

