package com.knowflow.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowflow.security.jwt")
public class JwtProperties {

    private String secret;
    private Long expirationMinutes = 1440L;
}

