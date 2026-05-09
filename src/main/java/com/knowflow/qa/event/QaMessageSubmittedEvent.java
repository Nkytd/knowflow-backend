package com.knowflow.qa.event;

public record QaMessageSubmittedEvent(Long messageId, String modelPreference) {
}
