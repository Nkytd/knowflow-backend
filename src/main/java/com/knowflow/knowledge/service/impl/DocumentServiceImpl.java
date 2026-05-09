package com.knowflow.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.integration.storage.FileStorageClient;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkIndexMapper;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.knowledge.service.DocumentService;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.knowledge.vo.DocumentBatchOperationVO;
import com.knowflow.knowledge.vo.DocumentPreviewVO;
import com.knowflow.knowledge.vo.KnowledgeDocumentVO;
import com.knowflow.parser.service.ParseTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String SOURCE_TYPE_FILE = "FILE";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final int PREVIEW_MAX_BYTES = 32 * 1024;
    private static final Set<String> PREVIEWABLE_FILE_TYPES = Set.of(
            "txt", "md", "markdown", "faq", "log", "csv", "json", "yaml", "yml"
    );

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ParseTaskService parseTaskService;
    private final FileStorageClient fileStorageClient;
    private final CurrentUserProvider currentUserProvider;

    public DocumentServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                               KnowledgeChunkMapper knowledgeChunkMapper,
                               KnowledgeChunkIndexMapper knowledgeChunkIndexMapper,
                               KnowledgeBaseService knowledgeBaseService,
                               ParseTaskService parseTaskService,
                               FileStorageClient fileStorageClient,
                               CurrentUserProvider currentUserProvider) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.parseTaskService = parseTaskService;
        this.fileStorageClient = fileStorageClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        knowledgeBaseService.validateKnowledgeBaseExists(currentUser.tenantId(), knowledgeBaseId);

        String docCode = CodeGenerator.documentCode();
        String originalFilename = sanitizeFileName(file.getOriginalFilename());
        String objectName = buildObjectName(currentUser.tenantId(), knowledgeBaseId, docCode, originalFilename);
        String storagePath;
        try (InputStream inputStream = file.getInputStream()) {
            storagePath = fileStorageClient.upload(objectName, inputStream, file.getSize(), file.getContentType());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文档保存失败");
        }

        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setTenantId(currentUser.tenantId());
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setDocCode(docCode);
        entity.setDocName(originalFilename);
        entity.setSourceType(SOURCE_TYPE_FILE);
        entity.setStorageType(fileStorageClient.storageType());
        entity.setStoragePath(storagePath);
        entity.setFileType(resolveFileType(originalFilename));
        entity.setFileSize(file.getSize());
        entity.setVersionNo(1);
        entity.setStatus(STATUS_ENABLED);
        entity.setParseStatus(TASK_STATUS_PENDING);
        entity.setIndexStatus(TASK_STATUS_PENDING);
        entity.setChunkCount(0);
        knowledgeDocumentMapper.insert(entity);

        parseTaskService.createPendingParseTask(entity);
        knowledgeBaseService.refreshDocumentCount(currentUser.tenantId(), knowledgeBaseId);
        return toVO(entity);
    }

    @Override
    public PageResponse<KnowledgeDocumentVO> page(Integer pageNo, Integer pageSize, Long knowledgeBaseId, String status, String parseStatus) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Page<KnowledgeDocumentEntity> page = knowledgeDocumentMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, currentUser.tenantId())
                        .eq(knowledgeBaseId != null, KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(StringUtils.hasText(status), KnowledgeDocumentEntity::getStatus, status)
                        .eq(StringUtils.hasText(parseStatus), KnowledgeDocumentEntity::getParseStatus, parseStatus)
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public KnowledgeDocumentVO getById(Long id) {
        return toVO(getEntityById(currentUserProvider.getCurrentUser().tenantId(), id));
    }

    @Override
    public DocumentPreviewVO preview(Long id) {
        KnowledgeDocumentEntity entity = getEntityById(currentUserProvider.getCurrentUser().tenantId(), id);
        if (!isPreviewable(entity.getFileType())) {
            return DocumentPreviewVO.builder()
                    .documentId(entity.getId())
                    .docName(entity.getDocName())
                    .contentType(resolveContentType(entity))
                    .previewable(false)
                    .truncated(false)
                    .message("Preview is available for text, markdown, FAQ, csv, json, yaml, and log files only.")
                    .build();
        }

        try (InputStream inputStream = fileStorageClient.download(entity.getStoragePath())) {
            PreviewContent previewContent = readPreviewContent(inputStream);
            return DocumentPreviewVO.builder()
                    .documentId(entity.getId())
                    .docName(entity.getDocName())
                    .contentType(resolveContentType(entity))
                    .previewable(true)
                    .truncated(previewContent.truncated())
                    .previewText(previewContent.previewText())
                    .message(previewContent.previewText().isBlank() ? "Stored file is empty." : null)
                    .build();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to read stored document preview");
        }
    }

    @Override
    public DocumentDownloadContent download(Long id) {
        KnowledgeDocumentEntity entity = getEntityById(currentUserProvider.getCurrentUser().tenantId(), id);
        return new DocumentDownloadContent(
                entity.getDocName(),
                resolveContentType(entity),
                entity.getFileSize() == null ? 0L : entity.getFileSize(),
                fileStorageClient.download(entity.getStoragePath())
        );
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        KnowledgeDocumentEntity entity = getEntityById(currentUserProvider.getCurrentUser().tenantId(), id);
        entity.setStatus(status);
        knowledgeDocumentMapper.updateById(entity);
    }

    @Override
    @Transactional
    public DocumentBatchOperationVO batchUpdateStatus(List<Long> documentIds, String status) {
        List<KnowledgeDocumentEntity> entities = getEntitiesByIds(currentUserProvider.getCurrentUser().tenantId(), documentIds);
        for (KnowledgeDocumentEntity entity : entities) {
            entity.setStatus(status);
            knowledgeDocumentMapper.updateById(entity);
        }
        return DocumentBatchOperationVO.builder()
                .action("BATCH_STATUS")
                .affectedCount(entities.size())
                .documentIds(entities.stream().map(KnowledgeDocumentEntity::getId).toList())
                .build();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeDocumentEntity entity = getEntityById(currentUser.tenantId(), id);
        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocumentId, entity.getId())
        );
        knowledgeChunkIndexMapper.hardDeleteByDocumentId(entity.getId());
        knowledgeDocumentMapper.deleteById(entity.getId());
        knowledgeBaseService.refreshDocumentCount(currentUser.tenantId(), entity.getKnowledgeBaseId());
    }

    @Override
    @Transactional
    public DocumentBatchOperationVO batchDelete(List<Long> documentIds) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        List<KnowledgeDocumentEntity> entities = getEntitiesByIds(tenantId, documentIds);
        for (KnowledgeDocumentEntity entity : entities) {
            delete(entity.getId());
        }
        return DocumentBatchOperationVO.builder()
                .action("BATCH_DELETE")
                .affectedCount(entities.size())
                .documentIds(entities.stream().map(KnowledgeDocumentEntity::getId).toList())
                .build();
    }

    private KnowledgeDocumentEntity getEntityById(Long tenantId, Long id) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getId, id)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        return entity;
    }

    private List<KnowledgeDocumentEntity> getEntitiesByIds(Long tenantId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Document ids must not be empty");
        }
        List<KnowledgeDocumentEntity> entities = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>(documentIds);
        for (Long documentId : uniqueIds) {
            entities.add(getEntityById(tenantId, documentId));
        }
        return entities;
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocumentEntity entity) {
        return KnowledgeDocumentVO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .docCode(entity.getDocCode())
                .docName(entity.getDocName())
                .sourceType(entity.getSourceType())
                .storageType(entity.getStorageType())
                .storagePath(entity.getStoragePath())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .versionNo(entity.getVersionNo())
                .status(entity.getStatus())
                .parseStatus(entity.getParseStatus())
                .indexStatus(entity.getIndexStatus())
                .chunkCount(entity.getChunkCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String buildObjectName(Long tenantId, Long knowledgeBaseId, String docCode, String originalFilename) {
        return String.format(
                Locale.ROOT,
                "tenant-%d/kb-%d/%s-%s",
                tenantId,
                knowledgeBaseId,
                docCode,
                UUID.randomUUID() + "-" + originalFilename
        );
    }

    private String sanitizeFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "unnamed-file";
        }
        return originalFilename.replace("\\", "_").replace("/", "_");
    }

    private String resolveFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isPreviewable(String fileType) {
        return fileType != null && PREVIEWABLE_FILE_TYPES.contains(fileType.toLowerCase(Locale.ROOT));
    }

    private String resolveContentType(KnowledgeDocumentEntity entity) {
        MediaType mediaType = MediaTypeFactory.getMediaType(entity.getDocName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return mediaType.toString();
    }

    private PreviewContent readPreviewContent(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalRead = 0;
        boolean truncated = false;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            int writable = Math.min(read, PREVIEW_MAX_BYTES - totalRead);
            if (writable > 0) {
                outputStream.write(buffer, 0, writable);
                totalRead += writable;
            }
            if (writable < read) {
                truncated = true;
                break;
            }
        }
        String previewText = normalizePreviewText(outputStream.toString(StandardCharsets.UTF_8));
        return new PreviewContent(previewText, truncated);
    }

    private String normalizePreviewText(String previewText) {
        if (previewText == null) {
            return "";
        }
        return previewText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private record PreviewContent(String previewText, boolean truncated) {
    }
}
