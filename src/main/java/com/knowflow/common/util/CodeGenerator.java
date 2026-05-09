package com.knowflow.common.util;

import java.util.Locale;
import java.util.UUID;

public final class CodeGenerator {

    private CodeGenerator() {
    }

    public static String tenantCode() {
        return prefixedCode("TEN");
    }

    public static String knowledgeBaseCode() {
        return prefixedCode("KB");
    }

    public static String documentCode() {
        return prefixedCode("DOC");
    }

    public static String parseTaskCode() {
        return prefixedCode("TASK");
    }

    public static String deadLetterCode() {
        return prefixedCode("DLQ");
    }

    public static String ticketCode() {
        return prefixedCode("TKT");
    }

    public static String prefixedCode(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return prefix + suffix;
    }
}
