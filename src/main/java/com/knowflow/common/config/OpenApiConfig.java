package com.knowflow.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KnowFlow API")
                        .version("v1")
                        .description("Enterprise intelligent knowledge service and ticket collaboration platform API"));
    }
}

