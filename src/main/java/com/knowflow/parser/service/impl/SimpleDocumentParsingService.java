package com.knowflow.parser.service.impl;

import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.integration.storage.FileStorageClient;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.support.KnowledgeTextSanitizer;
import com.knowflow.parser.model.ParsedChunk;
import com.knowflow.parser.model.ParsedDocument;
import com.knowflow.parser.service.DocumentParsingService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SimpleDocumentParsingService implements DocumentParsingService {

    private static final int MAX_CHARS_PER_CHUNK = 800;
    private static final int MIN_PARAGRAPH_LENGTH = 10;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FAQ_QUESTION = Pattern.compile("^(?:Q|Question|问题|问)[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAQ_ANSWER = Pattern.compile("^(?:A|Answer|答案|答)[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private final FileStorageClient fileStorageClient;

    public SimpleDocumentParsingService(FileStorageClient fileStorageClient) {
        this.fileStorageClient = fileStorageClient;
    }

    @Override
    public ParsedDocument parse(KnowledgeDocumentEntity documentEntity) {
        if (!isSupportedTextFile(documentEntity.getFileType())) {
            throw new BizException(
                    ErrorCode.PARSE_TASK_FAILED,
                    "Unsupported file type for parser worker: " + documentEntity.getFileType()
            );
        }

        try (InputStream inputStream = fileStorageClient.download(documentEntity.getStoragePath())) {
            String text = isPdfFile(documentEntity.getFileType())
                    ? extractPdfText(inputStream)
                    : extractUtf8Text(inputStream);
            String normalizedText = normalizeText(text);
            List<ParsedChunk> chunks = splitByFileType(normalizedText, documentEntity.getFileType());
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCode.PARSE_TASK_FAILED, "Document contains no parsable text");
            }
            return ParsedDocument.builder()
                    .normalizedText(normalizedText)
                    .chunks(chunks)
                    .build();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARSE_TASK_FAILED, "Failed to read stored document");
        }
    }

    private boolean isSupportedTextFile(String fileType) {
        if (fileType == null) {
            return false;
        }
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "txt", "md", "markdown", "faq", "log", "csv", "json", "yaml", "yml", "pdf" -> true;
            default -> false;
        };
    }

    private boolean isPdfFile(String fileType) {
        return fileType != null && "pdf".equals(fileType.toLowerCase(Locale.ROOT));
    }

    private String extractUtf8Text(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private String extractPdfText(InputStream inputStream) throws IOException {
        byte[] content = readAllBytes(inputStream);
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new BizException(ErrorCode.PARSE_TASK_FAILED, "PDF contains no extractable text. OCR is required for scanned PDF files");
            }
            return text;
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private String normalizeText(String originalText) {
        String normalized = originalText
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replace('\u0000', ' ');

        normalized = normalized.replaceAll("[ ]{2,}", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return KnowledgeTextSanitizer.cleanForChunk(normalized);
    }

    private List<ParsedChunk> splitByFileType(String normalizedText, String fileType) {
        if (isFaqFile(fileType) || looksLikeFaq(normalizedText)) {
            List<ParsedChunk> faqChunks = splitFaqIntoChunks(normalizedText);
            if (!faqChunks.isEmpty()) {
                return faqChunks;
            }
        }
        if (isMarkdownFile(fileType)) {
            List<ParsedChunk> markdownChunks = splitMarkdownIntoChunks(normalizedText);
            if (!markdownChunks.isEmpty()) {
                return markdownChunks;
            }
        }
        return splitIntoChunks(normalizedText);
    }

    private boolean isMarkdownFile(String fileType) {
        if (fileType == null) {
            return false;
        }
        String normalizedFileType = fileType.toLowerCase(Locale.ROOT);
        return "md".equals(normalizedFileType) || "markdown".equals(normalizedFileType);
    }

    private boolean isFaqFile(String fileType) {
        return fileType != null && "faq".equals(fileType.toLowerCase(Locale.ROOT));
    }

    private boolean looksLikeFaq(String normalizedText) {
        int questionCount = 0;
        int answerCount = 0;
        for (String line : normalizedText.split("\n")) {
            String trimmed = line.trim();
            if (FAQ_QUESTION.matcher(trimmed).matches()) {
                questionCount++;
            } else if (FAQ_ANSWER.matcher(trimmed).matches()) {
                answerCount++;
            }
        }
        return questionCount > 0 && answerCount > 0;
    }

    private List<ParsedChunk> splitMarkdownIntoChunks(String normalizedText) {
        List<String> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder sectionBody = new StringBuilder();
        String currentHeadingPath = null;

        for (String line : normalizedText.split("\n")) {
            Matcher headingMatcher = MARKDOWN_HEADING.matcher(line);
            if (headingMatcher.matches()) {
                addMarkdownSection(sections, currentHeadingPath, sectionBody);
                int level = headingMatcher.group(1).length();
                String heading = cleanMarkdownHeading(headingMatcher.group(2));
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading);
                currentHeadingPath = String.join(" > ", headingStack);
                sectionBody.setLength(0);
                continue;
            }

            if (sectionBody.length() > 0) {
                sectionBody.append('\n');
            }
            sectionBody.append(line);
        }

        addMarkdownSection(sections, currentHeadingPath, sectionBody);
        return splitSegmentsIntoChunks(sections);
    }

    private void addMarkdownSection(List<String> sections, String headingPath, StringBuilder sectionBody) {
        String body = sectionBody.toString().trim();
        if (!hasMeaningfulText(headingPath) && !hasMeaningfulText(body)) {
            return;
        }

        StringBuilder section = new StringBuilder();
        if (hasMeaningfulText(headingPath)) {
            section.append(headingPath.trim());
        }
        if (hasMeaningfulText(body)) {
            if (section.length() > 0) {
                section.append("\n\n");
            }
            section.append(body);
        }
        sections.add(section.toString());
    }

    private String cleanMarkdownHeading(String heading) {
        return heading
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<ParsedChunk> splitFaqIntoChunks(String normalizedText) {
        List<String> entries = new ArrayList<>();
        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();
        boolean readingAnswer = false;

        for (String line : normalizedText.split("\n")) {
            String trimmed = line.trim();
            Matcher questionMatcher = FAQ_QUESTION.matcher(trimmed);
            Matcher answerMatcher = FAQ_ANSWER.matcher(trimmed);
            if (questionMatcher.matches()) {
                addFaqEntry(entries, currentQuestion, currentAnswer);
                currentQuestion = questionMatcher.group(1).trim();
                currentAnswer.setLength(0);
                readingAnswer = false;
                continue;
            }
            if (answerMatcher.matches()) {
                appendLine(currentAnswer, answerMatcher.group(1).trim());
                readingAnswer = true;
                continue;
            }
            if (!trimmed.isBlank() && currentQuestion != null) {
                if (readingAnswer) {
                    appendLine(currentAnswer, trimmed);
                } else {
                    currentQuestion = currentQuestion + " " + trimmed;
                }
            }
        }

        addFaqEntry(entries, currentQuestion, currentAnswer);
        return splitSegmentsIntoChunks(entries);
    }

    private void addFaqEntry(List<String> entries, String question, StringBuilder answer) {
        if (!hasMeaningfulText(question) || !hasMeaningfulText(answer)) {
            return;
        }
        entries.add("Q: " + question.trim() + "\nA: " + answer.toString().trim());
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!hasMeaningfulText(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line.trim());
    }

    private List<ParsedChunk> splitIntoChunks(String normalizedText) {
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : normalizedText.split("\\n\\n")) {
            paragraphs.add(paragraph);
        }
        List<ParsedChunk> chunks = splitSegmentsIntoChunks(paragraphs);
        if (chunks.isEmpty() && !normalizedText.isBlank()) {
            chunks.add(toChunk(1, normalizedText.substring(0, Math.min(normalizedText.length(), MAX_CHARS_PER_CHUNK))));
        }
        return chunks;
    }

    private List<ParsedChunk> splitSegmentsIntoChunks(List<String> segments) {
        List<ParsedChunk> chunks = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int chunkNo = 1;

        for (String segment : segments) {
            String cleanedSegment = segment == null ? "" : segment.trim();
            if (cleanedSegment.length() < MIN_PARAGRAPH_LENGTH) {
                continue;
            }

            if (cleanedSegment.length() > MAX_CHARS_PER_CHUNK) {
                if (builder.length() > 0) {
                    chunks.add(toChunk(chunkNo++, builder.toString().trim()));
                    builder.setLength(0);
                }
                for (String part : splitLongSegment(cleanedSegment)) {
                    chunks.add(toChunk(chunkNo++, part));
                }
                continue;
            }

            if (builder.length() > 0 && builder.length() + cleanedSegment.length() + 2 > MAX_CHARS_PER_CHUNK) {
                chunks.add(toChunk(chunkNo++, builder.toString().trim()));
                builder.setLength(0);
            }

            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(cleanedSegment);
        }

        if (builder.length() > 0) {
            chunks.add(toChunk(chunkNo, builder.toString().trim()));
        }
        return chunks;
    }

    private List<String> splitLongSegment(String segment) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < segment.length()) {
            int end = Math.min(segment.length(), start + MAX_CHARS_PER_CHUNK);
            if (end < segment.length()) {
                int splitAt = findSplitPoint(segment, start, end);
                if (splitAt > start) {
                    end = splitAt;
                }
            }
            String part = segment.substring(start, end).trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
            start = end;
            while (start < segment.length() && Character.isWhitespace(segment.charAt(start))) {
                start++;
            }
        }
        return parts;
    }

    private int findSplitPoint(String text, int start, int end) {
        int minimum = start + MAX_CHARS_PER_CHUNK / 2;
        for (int index = end - 1; index > minimum; index--) {
            char value = text.charAt(index);
            if (value == '\n' || Character.isWhitespace(value)) {
                return index;
            }
        }
        return end;
    }

    private boolean hasMeaningfulText(CharSequence value) {
        return value != null && !value.toString().trim().isBlank();
    }

    private ParsedChunk toChunk(int chunkNo, String content) {
        return ParsedChunk.builder()
                .chunkNo(chunkNo)
                .content(content)
                .charCount(content.length())
                .tokenCount(estimateTokenCount(content))
                .build();
    }

    private int estimateTokenCount(String content) {
        int trimmedLength = content.trim().length();
        return Math.max(1, trimmedLength / 4);
    }
}
