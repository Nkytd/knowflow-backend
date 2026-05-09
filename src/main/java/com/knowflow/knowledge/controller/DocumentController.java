package com.knowflow.knowledge.controller;

import com.knowflow.audit.annotation.AuditBizIdSource;
import com.knowflow.audit.annotation.OperationAudit;
import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.knowledge.dto.BatchDeleteDocumentsRequest;
import com.knowflow.knowledge.dto.BatchUpdateDocumentStatusRequest;
import com.knowflow.knowledge.dto.UpdateDocumentStatusRequest;
import com.knowflow.knowledge.service.DocumentService;
import com.knowflow.knowledge.vo.DocumentBatchOperationVO;
import com.knowflow.knowledge.vo.DocumentPreviewVO;
import com.knowflow.knowledge.vo.KnowledgeDocumentVO;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/documents")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','KNOWLEDGE_OPERATOR')")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    @OperationAudit(moduleCode = "DOCUMENT", actionCode = "UPLOAD", bizType = "DOCUMENT",
            summary = "上传知识文档", bizNoField = "docCode")
    public ApiResponse<KnowledgeDocumentVO> upload(@RequestParam Long knowledgeBaseId,
                                                   @RequestPart MultipartFile file) {
        return ApiResponse.success(documentService.upload(knowledgeBaseId, file));
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeDocumentVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                                               @RequestParam(required = false) Long knowledgeBaseId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String parseStatus) {
        return ApiResponse.success(documentService.page(pageNo, pageSize, knowledgeBaseId, status, parseStatus));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeDocumentVO> detail(@PathVariable Long id) {
        return ApiResponse.success(documentService.getById(id));
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<DocumentPreviewVO> preview(@PathVariable Long id) {
        return ApiResponse.success(documentService.preview(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        DocumentService.DocumentDownloadContent content = documentService.download(id);
        MediaType mediaType = MediaType.parseMediaType(content.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(content.inputStream()));
    }

    @PutMapping("/{id}/status")
    @OperationAudit(moduleCode = "DOCUMENT", actionCode = "UPDATE_STATUS", bizType = "DOCUMENT",
            summary = "更新文档状态", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateDocumentStatusRequest request) {
        documentService.updateStatus(id, request.getStatus());
        return ApiResponse.success();
    }

    @PutMapping("/batch/status")
    @OperationAudit(moduleCode = "DOCUMENT", actionCode = "BATCH_UPDATE_STATUS", bizType = "DOCUMENT",
            summary = "批量更新文档状态")
    public ApiResponse<DocumentBatchOperationVO> batchUpdateStatus(@Valid @RequestBody BatchUpdateDocumentStatusRequest request) {
        return ApiResponse.success(documentService.batchUpdateStatus(request.getDocumentIds(), request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @OperationAudit(moduleCode = "DOCUMENT", actionCode = "DELETE", bizType = "DOCUMENT",
            summary = "删除文档", bizIdSource = AuditBizIdSource.FIRST_LONG_ARG)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/batch/delete")
    @OperationAudit(moduleCode = "DOCUMENT", actionCode = "BATCH_DELETE", bizType = "DOCUMENT",
            summary = "批量删除文档")
    public ApiResponse<DocumentBatchOperationVO> batchDelete(@Valid @RequestBody BatchDeleteDocumentsRequest request) {
        return ApiResponse.success(documentService.batchDelete(request.getDocumentIds()));
    }
}
