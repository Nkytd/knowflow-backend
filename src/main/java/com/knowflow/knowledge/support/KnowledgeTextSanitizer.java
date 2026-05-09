package com.knowflow.knowledge.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes PDF/web-page header and footer noise before chunks are indexed or sent to the LLM.
 */
public final class KnowledgeTextSanitizer {

    private static final Pattern URL_LINE = Pattern.compile("^https?://\\S+(?:\\s+\\d+\\s*/\\s*\\d+)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_COUNTER_LINE = Pattern.compile("^(?:第\\s*)?\\d+\\s*/\\s*\\d+\\s*(?:页)?$");
    private static final Pattern TIMESTAMP_TITLE_LINE = Pattern.compile("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}\\s+\\d{1,2}:\\d{2}.*$");
    private static final Pattern SITE_TITLE_LINE = Pattern.compile(".*\\|\\s*小林coding\\s*\\|.*");
    private static final Pattern XIAOLIN_HEADER_LINE = Pattern.compile(".*小林coding.*Java.*面试.*");
    private static final Pattern BROWSER_PRINT_LINE = Pattern.compile(".*(?:Java基础面试题|Java面试学习).*小林coding.*");
    private static final Pattern MOSTLY_URL_WITH_PAGE = Pattern.compile(".*https?://\\S+.*\\b\\d+\\s*/\\s*\\d+\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("[ \\u00A0]{2,}");

    private KnowledgeTextSanitizer() {
    }

    public static String cleanForChunk(String rawText) {
        return clean(rawText, true);
    }

    public static String cleanForPrompt(String rawText) {
        return clean(rawText, false);
    }

    private static String clean(String rawText, boolean dropTinyLines) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String normalized = rawText
                .replace("\\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replace('\u0000', ' ');

        String[] lines = normalized.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        String previousKept = null;
        for (String line : lines) {
            String trimmed = WHITESPACE.matcher(line.trim()).replaceAll(" ");
            if (shouldDropLine(trimmed, dropTinyLines)) {
                continue;
            }
            if (trimmed.equals(previousKept)) {
                continue;
            }
            cleanedLines.add(trimmed);
            previousKept = trimmed;
        }

        String cleaned = String.join("\n", cleanedLines);
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    private static boolean shouldDropLine(String line, boolean dropTinyLines) {
        if (line.isBlank()) {
            return false;
        }
        if (URL_LINE.matcher(line).matches()
                || PAGE_COUNTER_LINE.matcher(line).matches()
                || TIMESTAMP_TITLE_LINE.matcher(line).matches()
                || SITE_TITLE_LINE.matcher(line).matches()
                || XIAOLIN_HEADER_LINE.matcher(line).matches()
                || BROWSER_PRINT_LINE.matcher(line).matches()
                || MOSTLY_URL_WITH_PAGE.matcher(line).matches()) {
            return true;
        }
        if (dropTinyLines && line.length() <= 2 && line.matches("[\\p{Punct}·•\\-]+")) {
            return true;
        }
        return false;
    }
}
