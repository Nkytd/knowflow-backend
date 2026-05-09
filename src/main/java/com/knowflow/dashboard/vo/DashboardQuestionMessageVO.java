package com.knowflow.dashboard.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DashboardQuestionMessageVO {

    private Long id;
    private Long sessionId;
    private String sessionTitle;
    private String questionText;
    private String answerText;
    private String answerStatus;
    private Integer sourceCount;
    private Boolean needHumanHandoff;
    private LocalDateTime createdAt;
}
