package com.knowflow.parser.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.parser.vo.ParseTaskVO;

public interface ParseTaskService {

    void createPendingParseTask(KnowledgeDocumentEntity documentEntity);

    void createPendingIndexTask(KnowledgeDocumentEntity documentEntity);

    PageResponse<ParseTaskVO> page(Integer pageNo, Integer pageSize, Long documentId, String status, String taskType);

    ParseTaskVO getById(Long id);

    void retry(Long id);

    void retrySystem(Long taskId);

    Long findLatestTaskIdByDocumentId(Long documentId);
}
