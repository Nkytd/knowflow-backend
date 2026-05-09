package com.knowflow.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAudit {

    String moduleCode();

    String actionCode();

    String bizType();

    String summary();

    AuditBizIdSource bizIdSource() default AuditBizIdSource.RESULT_ID;

    String bizIdField() default "id";

    String bizNoField() default "";
}
