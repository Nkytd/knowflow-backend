package com.knowflow.integration.storage;

import java.io.InputStream;

public interface FileStorageClient {

    String upload(String objectName, InputStream inputStream, long size, String contentType);

    InputStream download(String objectName);

    String storageType();
}
