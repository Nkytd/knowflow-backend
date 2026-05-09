package com.knowflow.parser.service.impl;

import com.knowflow.integration.storage.FileStorageClient;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.parser.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleDocumentParsingServiceTests {

    private InMemoryFileStorageClient fileStorageClient;
    private SimpleDocumentParsingService parsingService;

    @BeforeEach
    void setUp() {
        fileStorageClient = new InMemoryFileStorageClient();
        parsingService = new SimpleDocumentParsingService(fileStorageClient);
    }

    @Test
    void shouldKeepMarkdownHeadingContextInChunks() {
        String markdown = """
                # Operations Handbook

                ## VPN

                Restart the desktop client before reconnecting to the VPN gateway.

                Confirm that the certificate warning is no longer displayed.

                ## Password Reset

                Open the identity portal and complete MFA before changing the password.
                """;
        fileStorageClient.put("operations.md", markdown);

        ParsedDocument parsedDocument = parsingService.parse(document("operations.md", "md"));

        assertThat(parsedDocument.getChunks())
                .hasSize(1)
                .first()
                .satisfies(chunk -> {
                    assertThat(chunk.getContent()).contains("Operations Handbook > VPN");
                    assertThat(chunk.getContent()).contains("Restart the desktop client");
                    assertThat(chunk.getContent()).contains("Operations Handbook > Password Reset");
                });
    }

    @Test
    void shouldParseFaqQuestionAndAnswerPairsAsSemanticSegments() {
        String faq = """
                Q: How do I reset VPN access?
                A: Ask the tenant administrator to reset the VPN device binding.

                Q: What should I do when MFA fails?
                A: Check the system clock first.
                Then retry the push notification from the identity portal.
                """;
        fileStorageClient.put("support.faq", faq);

        ParsedDocument parsedDocument = parsingService.parse(document("support.faq", "faq"));

        assertThat(parsedDocument.getChunks())
                .hasSize(1)
                .first()
                .satisfies(chunk -> {
                    assertThat(chunk.getContent()).contains("Q: How do I reset VPN access?");
                    assertThat(chunk.getContent()).contains("A: Ask the tenant administrator");
                    assertThat(chunk.getContent()).contains("Q: What should I do when MFA fails?");
                    assertThat(chunk.getContent()).contains("Then retry the push notification");
                });
    }

    private KnowledgeDocumentEntity document(String storagePath, String fileType) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setStoragePath(storagePath);
        document.setFileType(fileType);
        return document;
    }

    private static class InMemoryFileStorageClient implements FileStorageClient {

        private final Map<String, byte[]> files = new HashMap<>();

        void put(String objectName, String content) {
            files.put(objectName, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String upload(String objectName, InputStream inputStream, long size, String contentType) {
            throw new UnsupportedOperationException("Upload is not needed in parser unit tests");
        }

        @Override
        public InputStream download(String objectName) {
            return new ByteArrayInputStream(files.get(objectName));
        }

        @Override
        public String storageType() {
            return "MEMORY";
        }
    }
}
