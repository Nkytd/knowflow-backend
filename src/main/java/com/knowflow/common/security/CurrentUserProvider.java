package com.knowflow.common.security;

import com.knowflow.auth.security.AuthUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CurrentUserProvider {

    private static final CurrentUser SYSTEM_USER = new CurrentUser(0L, 0L, "system", "System", List.of());

    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_USER;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUserPrincipal authUserPrincipal) {
            return new CurrentUser(
                    authUserPrincipal.getUserId(),
                    authUserPrincipal.getTenantId(),
                    authUserPrincipal.getUsername(),
                    authUserPrincipal.getRealName(),
                    authUserPrincipal.getRoleCodes()
            );
        }
        return SYSTEM_USER;
    }
}

