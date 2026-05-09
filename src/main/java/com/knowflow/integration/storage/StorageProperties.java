package com.knowflow.integration.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowflow.storage")
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private Minio minio = new Minio();

    @Getter
    @Setter
    public static class Local {
        private String basePath = "./data/uploads";
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "knowflow";
        private boolean autoCreateBucket = true;
    }
}
