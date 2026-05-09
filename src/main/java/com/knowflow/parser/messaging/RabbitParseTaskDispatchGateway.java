package com.knowflow.parser.messaging;

import com.knowflow.parser.config.ParseTaskMessagingProperties;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "rabbit")
public class RabbitParseTaskDispatchGateway implements ParseTaskDispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(RabbitParseTaskDispatchGateway.class);

    private final RabbitTemplate rabbitTemplate;
    private final ParseTaskMessagingProperties properties;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;

    public RabbitParseTaskDispatchGateway(RabbitTemplate rabbitTemplate,
                                          ParseTaskMessagingProperties properties,
                                          ParseTaskRuntimeTracker parseTaskRuntimeTracker) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
    }

    @Override
    public void dispatch(Long taskId) {
        try {
            rabbitTemplate.convertAndSend(
                    properties.getExchange(),
                    properties.getRoutingKey(),
                    new ParseTaskDispatchMessage(taskId, LocalDateTime.now())
            );
            parseTaskRuntimeTracker.markQueued(taskId, "RABBIT");
            log.info("Dispatched parse task to RabbitMQ. taskId={}, exchange={}, routingKey={}",
                    taskId,
                    properties.getExchange(),
                    properties.getRoutingKey());
        } catch (AmqpException ex) {
            parseTaskRuntimeTracker.markDispatchFailed(taskId, ex.getMessage());
            throw ex;
        }
    }
}
