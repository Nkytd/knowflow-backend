package com.knowflow.devtools;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BackendPdfKnowledgeImporter {

    private static final long TENANT_ID = 1L;
    private static final long KNOWLEDGE_BASE_ID = 10L;
    private static final int MAX_CHARS_PER_CHUNK = 800;
    private static final int MIN_PARAGRAPH_LENGTH = 10;
    private static final int EMBEDDING_DIM = 128;

    public static void main(String[] args) throws Exception {
        String pdfDir = args.length > 0 ? args[0] : ".";
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/knowflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
                "root",
                "root")) {
            connection.setAutoCommit(false);
            clearExistingChunks(connection);
            int totalChunks = 0;
            for (DocumentSource source : listSources(pdfDir)) {
                int chunkCount = importOne(connection, source);
                totalChunks += chunkCount;
                System.out.printf("Imported documentId=%d file=%s chunks=%d%n", source.documentId(), source.fileName(), chunkCount);
            }
            connection.commit();
            System.out.printf("Import finished. totalChunks=%d%n", totalChunks);
        }
    }

    private static List<DocumentSource> listSources(String pdfDir) {
        return List.of(
                new DocumentSource(20L, "docker-interview.pdf"),
                new DocumentSource(21L, "java-basic-interview.pdf"),
                new DocumentSource(22L, "java-concurrency-interview.pdf"),
                new DocumentSource(23L, "java-collection-interview.pdf"),
                new DocumentSource(24L, "linux-command-interview.pdf"),
                new DocumentSource(25L, "mysql-interview.pdf"),
                new DocumentSource(26L, "redis-interview.pdf"),
                new DocumentSource(27L, "message-queue-interview.pdf"),
                new DocumentSource(28L, "network-interview.pdf")
        ).stream().map(source -> source.withPath(new File(pdfDir, source.fileName()).getAbsolutePath())).toList();
    }

    private static void clearExistingChunks(Connection connection) throws Exception {
        try (PreparedStatement deleteIndex = connection.prepareStatement("DELETE FROM knowledge_chunk_index WHERE document_id BETWEEN 20 AND 28");
             PreparedStatement deleteChunks = connection.prepareStatement("DELETE FROM knowledge_chunk WHERE document_id BETWEEN 20 AND 28")) {
            deleteIndex.executeUpdate();
            deleteChunks.executeUpdate();
        }
    }

    private static int importOne(Connection connection, DocumentSource source) throws Exception {
        String text = extractPdfText(source.path());
        List<String> chunks = splitIntoChunks(normalizeText(text));
        int chunkNo = 1;
        for (String chunk : chunks) {
            long chunkId = insertChunk(connection, source.documentId(), chunkNo, chunk);
            insertChunkIndex(connection, source.documentId(), chunkId, chunk);
            chunkNo++;
        }
        updateDocumentStatus(connection, source.documentId(), chunks.size());
        updateParseTasks(connection, source.documentId(), chunks.size());
        return chunks.size();
    }

    private static String extractPdfText(String path) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("PDF contains no extractable text: " + path);
            }
            return text;
        }
    }

    private static String normalizeText(String originalText) {
        String normalized = originalText
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replace('\u0000', ' ');
        normalized = normalized.replaceAll("[ ]{2,}", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    private static List<String> splitIntoChunks(String normalizedText) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = normalizedText.split("\\n\\n");
        StringBuilder builder = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleaned = paragraph.trim();
            if (cleaned.length() < MIN_PARAGRAPH_LENGTH) {
                continue;
            }
            if (cleaned.length() > MAX_CHARS_PER_CHUNK) {
                if (builder.length() > 0) {
                    chunks.add(builder.toString().trim());
                    builder.setLength(0);
                }
                chunks.addAll(splitLongParagraph(cleaned));
                continue;
            }
            if (builder.length() > 0 && builder.length() + cleaned.length() + 2 > MAX_CHARS_PER_CHUNK) {
                chunks.add(builder.toString().trim());
                builder.setLength(0);
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(cleaned);
        }
        if (builder.length() > 0) {
            chunks.add(builder.toString().trim());
        }
        return chunks;
    }

    private static List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + MAX_CHARS_PER_CHUNK);
            String part = paragraph.substring(start, end).trim();
            if (part.length() >= MIN_PARAGRAPH_LENGTH) {
                parts.add(part);
            }
            start = end;
        }
        return parts;
    }

    private static long insertChunk(Connection connection, long documentId, int chunkNo, String content) throws Exception {
        String sql = "INSERT INTO knowledge_chunk (tenant_id, knowledge_base_id, document_id, chunk_no, content, char_count, token_count, status, created_by, updated_by, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 'ENABLED', 2, 2, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, TENANT_ID);
            statement.setLong(2, KNOWLEDGE_BASE_ID);
            statement.setLong(3, documentId);
            statement.setInt(4, chunkNo);
            statement.setString(5, content);
            statement.setInt(6, content.length());
            statement.setInt(7, Math.max(1, content.length() / 4));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Failed to get generated chunk id");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void insertChunkIndex(Connection connection, long documentId, long chunkId, String content) throws Exception {
        List<Float> embedding = embed(content);
        String sql = "INSERT INTO knowledge_chunk_index (tenant_id, knowledge_base_id, document_id, chunk_id, embedding_provider, embedding_model, embedding_dim, vector_norm, embedding_json, status, created_by, updated_by, deleted) VALUES (?, ?, ?, ?, 'LOCAL_HASH', 'local-hash-embedding', ?, ?, ?, 'ENABLED', 2, 2, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, TENANT_ID);
            statement.setLong(2, KNOWLEDGE_BASE_ID);
            statement.setLong(3, documentId);
            statement.setLong(4, chunkId);
            statement.setInt(5, EMBEDDING_DIM);
            statement.setBigDecimal(6, BigDecimal.valueOf(vectorNorm(embedding)).setScale(8, RoundingMode.HALF_UP));
            statement.setString(7, toJson(embedding));
            statement.executeUpdate();
        }
    }

    private static void updateDocumentStatus(Connection connection, long documentId, int chunkCount) throws Exception {
        String sql = "UPDATE knowledge_document SET parse_status='SUCCESS', index_status='SUCCESS', chunk_count=?, updated_at=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, chunkCount);
            statement.setObject(2, LocalDateTime.now());
            statement.setLong(3, documentId);
            statement.executeUpdate();
        }
    }

    private static void updateParseTasks(Connection connection, long documentId, int chunkCount) throws Exception {
        String sql = "UPDATE parse_task SET status='SUCCESS', error_message=NULL, finished_at=?, duration_ms=0, updated_at=? WHERE document_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            statement.setObject(1, now);
            statement.setObject(2, now);
            statement.setLong(3, documentId);
            statement.executeUpdate();
        }
    }

    private static List<Float> embed(String text) throws Exception {
        float[] vector = new float[EMBEDDING_DIM];
        for (String token : tokenize(normalizeForEmbedding(text))) {
            int bucket = Math.floorMod(token.hashCode(), EMBEDDING_DIM);
            vector[bucket] += Math.max(1, token.length() / 2.0f);
        }
        double norm = 0D;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0D) {
            double sqrt = Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / sqrt);
            }
        }
        List<Float> result = new ArrayList<>(EMBEDDING_DIM);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private static String normalizeForEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        for (String token : text.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
            if (containsCjk(token) && token.length() >= 2) {
                for (int i = 0; i < token.length() - 1; i++) {
                    tokens.add(token.substring(i, i + 2));
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    private static boolean containsCjk(String token) {
        return token.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static double vectorNorm(List<Float> embedding) {
        double sum = 0D;
        for (Float value : embedding) {
            if (value != null) {
                sum += value * value;
            }
        }
        return Math.sqrt(sum);
    }

    private static String toJson(List<Float> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", values.get(i)));
        }
        return builder.append(']').toString();
    }

    private record DocumentSource(long documentId, String fileName, String path) {
        private DocumentSource(long documentId, String fileName) {
            this(documentId, fileName, null);
        }

        private DocumentSource withPath(String path) {
            return new DocumentSource(documentId, fileName, path);
        }
    }
}
