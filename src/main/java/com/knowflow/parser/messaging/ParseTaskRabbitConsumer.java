package com.knowflow.parser.messaging;

import com.knowflow.parser.deadletter.support.TaskExecutionFailedException;
import com.knowflow.parser.support.TaskWorkerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "rabbit")
public class ParseTaskRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskRabbitConsumer.class);

    private final TaskWorkerRouter taskWorkerRouter;

    public ParseTaskRabbitConsumer(TaskWorkerRouter taskWorkerRouter) {
        this.taskWorkerRouter = taskWorkerRouter;
    }

    @RabbitListener(queues = "${knowflow.parser.messaging.queue:knowflow.parse.task.queue}")
    public void consume(ParseTaskDispatchMessage message) {
        if (message == null || message.taskId() == null) {
            log.warn("Received empty parse task message");
            return;
        }
        log.info("Received parse task message from RabbitMQ. taskId={}", message.taskId());
        try {
            taskWorkerRouter.process(message.taskId(), true);
        } catch (TaskExecutionFailedException ex) {
            throw new AmqpRejectAndDontRequeueException("Task processing failed and should be dead-lettered", ex);
        }
    }
}
