package com.knowflow.common.security;

import java.util.List;

public record CurrentUser(Long userId,
                          Long tenantId,
                          String username,
                          String realName,
                          List<String> roleCodes) {
}

