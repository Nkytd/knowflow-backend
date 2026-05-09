package com.knowflow.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogVO {

    private Long id;
    private String moduleCode;
    private String actionCode;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private Long operatorUserId;
    private String operatorUsername;
    private String operatorRealName;
    private String requestMethod;
    private String requestUri;
    private String operationSummary;
    private Boolean successFlag;
    private String errorMessage;
    private LocalDateTime createdAt;
}
