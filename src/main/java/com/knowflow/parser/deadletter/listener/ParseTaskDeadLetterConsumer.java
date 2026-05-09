package com.knowflow.parser.deadletter.listener;

import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.messaging.ParseTaskDispatchMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "rabbit")
public class ParseTaskDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskDeadLetterConsumer.class);

    private final DeadLetterMessageService deadLetterMessageService;

    public ParseTaskDeadLetterConsumer(DeadLetterMessageService deadLetterMessageService) {
        this.deadLetterMessageService = deadLetterMessageService;
    }

    @RabbitListener(queues = "${knowflow.parser.messaging.dead-letter-queue:knowflow.parse.task.dlq}")
    public void consume(ParseTaskDispatchMessage message,
                        @Header(value = AmqpHeaders.CONSUMER_QUEUE, required = false) String queue,
                        @Header(value = AmqpHeaders.RECEIVED_EXCHANGE, required = false) String exchange,
                        @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        if (message == null || message.taskId() == null) {
            log.warn("Received empty dead-letter message");
            return;
        }
        deadLetterMessageService.recordDeadLetter(message, queue, exchange, routingKey);
    }
}
