package com.knowflow.qa.listener;

import com.knowflow.qa.event.QaMessageSubmittedEvent;
import com.knowflow.qa.service.impl.QaMessageServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QaMessageEventListener {

    private static final Logger log = LoggerFactory.getLogger(QaMessageEventListener.class);

    private final QaMessageServiceImpl qaMessageService;

    public QaMessageEventListener(QaMessageServiceImpl qaMessageService) {
        this.qaMessageService = qaMessageService;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(QaMessageSubmittedEvent event) {
        try {
            qaMessageService.processSubmittedMessage(event.messageId(), event.modelPreference());
        } catch (Exception ex) {
            log.error("Failed to process QA message {}", event.messageId(), ex);
        }
    }
}