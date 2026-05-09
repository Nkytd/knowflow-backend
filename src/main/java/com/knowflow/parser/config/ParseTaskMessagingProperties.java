package com.knowflow.parser.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowflow.parser.messaging")
public class ParseTaskMessagingProperties {

    private String mode = "local";
    private String exchange = "knowflow.parse.task.exchange";
    private String queue = "knowflow.parse.task.queue";
    private String routingKey = "knowflow.parse.task.submit";
    private String deadLetterExchange = "knowflow.parse.task.dlx";
    private String deadLetterQueue = "knowflow.parse.task.dlq";
    private String deadLetterRoutingKey = "knowflow.parse.task.dead";
    private long runtimeCacheTtlHours = 24;
    private long workerLockSeconds = 600;
    private long staleProcessingMinutes = 10;
    private int startupRecoveryLimit = 200;
    private boolean autoRetryEnabled = true;
    private int maxAutoRetries = 2;
    private long autoRetryBaseDelaySeconds = 30;
    private long autoRetryIntervalMs = 15000;
    private int autoRetryBatchSize = 20;

    public boolean isRabbitMode() {
        return "rabbit".equalsIgnoreCase(mode);
    }
}
