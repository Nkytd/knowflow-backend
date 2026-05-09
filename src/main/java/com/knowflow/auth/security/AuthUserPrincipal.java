package com.knowflow.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final String realName;
    private final String passwordHash;
    private final String status;
    private final List<String> roleCodes;
    private final List<GrantedAuthority> authorities;

    public AuthUserPrincipal(Long userId,
                             Long tenantId,
                             String username,
                             String realName,
                             String passwordHash,
                             String status,
                             List<String> roleCodes) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.realName = realName;
        this.passwordHash = passwordHash;
        this.status = status;
        this.roleCodes = roleCodes;
        this.authorities = roleCodes.stream()
                .map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return "ENABLED".equals(status);
    }
}

