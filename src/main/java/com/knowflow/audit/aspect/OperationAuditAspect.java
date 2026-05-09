package com.knowflow.audit.aspect;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.audit.entity.AuditLogEntity;
import com.knowflow.audit.service.AuditLogService;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Aspect
@Component
public class OperationAuditAspect {

    private static final List<String> DEFAULT_BIZ_NO_FIELDS = List.of("ticketNo", "sourceTicketNo", "sessionNo", "title");

    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;

    public OperationAuditAspect(AuditLogService auditLogService, CurrentUserProvider currentUserProvider) {
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
    }

    @Around("@annotation(operationAudit)")
    public Object around(ProceedingJoinPoint joinPoint, OperationAudit operationAudit) throws Throwable {
        Object result = null;
        Throwable throwable = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            auditSafely(buildAuditLog(joinPoint, operationAudit, result, throwable));
        }
    }

    private AuditLogEntity buildAuditLog(ProceedingJoinPoint joinPoint,
                                         OperationAudit operationAudit,
                                         Object result,
                                         Throwable throwable) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        HttpServletRequest request = currentRequest();
        Object responseData = unwrapResponseData(result);
        return AuditLogEntity.builder()
                .tenantId(currentUser.tenantId())
                .moduleCode(normalize(operationAudit.moduleCode()))
                .actionCode(normalize(operationAudit.actionCode()))
                .bizType(normalize(operationAudit.bizType()))
                .bizId(resolveBizId(operationAudit, joinPoint.getArgs(), responseData))
                .bizNo(resolveBizNo(operationAudit, responseData))
                .operatorUserId(currentUser.userId())
                .operatorUsername(currentUser.username())
                .operatorRealName(currentUser.realName())
                .requestMethod(request == null ? null : request.getMethod())
                .requestUri(request == null ? null : trimToLength(request.getRequestURI(), 512))
                .operationSummary(trimToLength(operationAudit.summary(), 500))
                .successFlag(throwable == null ? 1 : 0)
                .errorMessage(throwable == null ? null : trimToLength(throwable.getMessage(), 1000))
                .build();
    }

    private Long resolveBizId(OperationAudit operationAudit, Object[] args, Object responseData) {
        if (operationAudit.bizIdSource() == AuditBizIdSource.FIRST_LONG_ARG) {
            return Arrays.stream(args)
                    .filter(Long.class::isInstance)
                    .map(Long.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        Object value = readProperty(responseData, operationAudit.bizIdField());
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveBizNo(OperationAudit operationAudit, Object responseData) {
        if (StringUtils.hasText(operationAudit.bizNoField())) {
            Object value = readProperty(responseData, operationAudit.bizNoField());
            return trimToLength(value == null ? null : String.valueOf(value), 255);
        }
        for (String field : DEFAULT_BIZ_NO_FIELDS) {
            Object value = readProperty(responseData, field);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return trimToLength(String.valueOf(value), 255);
            }
        }
        return null;
    }

    private Object unwrapResponseData(Object result) {
        if (result instanceof ApiResponse<?> apiResponse) {
            return apiResponse.getData();
        }
        return result;
    }

    private Object readProperty(Object target, String propertyName) {
        if (target == null || !StringUtils.hasText(propertyName)) {
            return null;
        }
        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(target);
        if (!beanWrapper.isReadableProperty(propertyName)) {
            return null;
        }
        return beanWrapper.getPropertyValue(propertyName);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private void auditSafely(AuditLogEntity entity) {
        try {
            auditLogService.record(entity);
        } catch (Exception ex) {
            log.warn("Failed to persist operation audit log: {}", ex.getMessage());
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : value;
    }

    private String trimToLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
