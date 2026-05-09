package com.knowflow.knowledge.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.knowledge.dto.CreateKnowledgeBaseRequest;
import com.knowflow.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.knowflow.knowledge.vo.KnowledgeBaseOptionVO;
import com.knowflow.knowledge.vo.KnowledgeBaseVO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO create(CreateKnowledgeBaseRequest request);

    PageResponse<KnowledgeBaseVO> page(Integer pageNo, Integer pageSize, String keyword, String status);

    List<KnowledgeBaseOptionVO> listEnabledOptions();

    KnowledgeBaseVO getById(Long id);

    KnowledgeBaseVO update(Long id, UpdateKnowledgeBaseRequest request);

    void updateStatus(Long id, String status);

    void validateKnowledgeBaseExists(Long tenantId, Long knowledgeBaseId);

    void refreshDocumentCount(Long tenantId, Long knowledgeBaseId);
}
