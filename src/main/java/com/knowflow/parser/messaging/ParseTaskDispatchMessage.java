package com.knowflow.parser.messaging;

import java.time.LocalDateTime;

public record ParseTaskDispatchMessage(Long taskId, LocalDateTime submittedAt) {
}
