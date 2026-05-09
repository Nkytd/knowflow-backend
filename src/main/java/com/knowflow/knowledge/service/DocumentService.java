package com.knowflow.knowledge.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.knowledge.vo.DocumentBatchOperationVO;
import com.knowflow.knowledge.vo.DocumentPreviewVO;
import com.knowflow.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface DocumentService {

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file);

    PageResponse<KnowledgeDocumentVO> page(Integer pageNo, Integer pageSize, Long knowledgeBaseId, String status, String parseStatus);

    KnowledgeDocumentVO getById(Long id);

    DocumentPreviewVO preview(Long id);

    DocumentDownloadContent download(Long id);

    void updateStatus(Long id, String status);

    DocumentBatchOperationVO batchUpdateStatus(List<Long> documentIds, String status);

    void delete(Long id);

    DocumentBatchOperationVO batchDelete(List<Long> documentIds);

    record DocumentDownloadContent(String fileName, String contentType, long contentLength, InputStream inputStream) {
    }
}
