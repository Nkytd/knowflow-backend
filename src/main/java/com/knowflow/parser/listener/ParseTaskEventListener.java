package com.knowflow.parser.listener;

import com.knowflow.parser.messaging.ParseTaskDispatchGateway;
import com.knowflow.parser.event.ParseTaskSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParseTaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskEventListener.class);

    private final ParseTaskDispatchGateway parseTaskDispatchGateway;

    public ParseTaskEventListener(ParseTaskDispatchGateway parseTaskDispatchGateway) {
        this.parseTaskDispatchGateway = parseTaskDispatchGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ParseTaskSubmittedEvent event) {
        try {
            parseTaskDispatchGateway.dispatch(event.taskId());
        } catch (Exception ex) {
            log.error("Failed to dispatch parse task {}", event.taskId(), ex);
        }
    }
}
