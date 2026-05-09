package com.knowflow.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitFeedbackRequest {

    @NotBlank(message = "feedbackType cannot be empty")
    private String feedbackType;

    private String feedbackReason;
}

