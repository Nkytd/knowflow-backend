package com.knowflow.integration.storage.minio;

import com.knowflow.integration.storage.FileStorageClient;
import com.knowflow.integration.storage.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
@ConditionalOnProperty(name = "knowflow.storage.type", havingValue = "minio")
public class MinioFileStorageClient implements FileStorageClient {

    private final StorageProperties.Minio properties;
    private final MinioClient minioClient;
    private volatile boolean bucketReady;

    public MinioFileStorageClient(StorageProperties storageProperties) {
        this.properties = storageProperties.getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint(this.properties.getEndpoint())
                .credentials(this.properties.getAccessKey(), this.properties.getSecretKey())
                .build();
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size, String contentType) {
        ensureBucket();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build()
            );
            return objectName;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload file to MinIO", ex);
        }
    }

    @Override
    public InputStream download(String objectName) {
        ensureBucket();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download file from MinIO", ex);
        }
    }

    @Override
    public String storageType() {
        return "MINIO";
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(properties.getBucket()).build()
                );
                if (!exists) {
                    if (!properties.isAutoCreateBucket()) {
                        throw new IllegalStateException("MinIO bucket does not exist: " + properties.getBucket());
                    }
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to initialize MinIO bucket", ex);
            }
        }
    }
}
