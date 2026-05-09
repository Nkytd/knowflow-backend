package com.knowflow.integration.storage.local;

import com.knowflow.integration.storage.StorageProperties;
import com.knowflow.integration.storage.FileStorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(name = "knowflow.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageClient implements FileStorageClient {

    private final Path basePath;

    public LocalFileStorageClient(StorageProperties storageProperties) {
        this.basePath = Paths.get(storageProperties.getLocal().getBasePath()).toAbsolutePath().normalize();
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            Path targetPath = basePath.resolve(objectName).normalize();
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return objectName;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store file locally", ex);
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            Path targetPath = basePath.resolve(objectName).normalize();
            return Files.newInputStream(targetPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read local file", ex);
        }
    }

    @Override
    public String storageType() {
        return "LOCAL";
    }
}
