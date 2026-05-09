package com.knowflow.common.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdUtil {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

