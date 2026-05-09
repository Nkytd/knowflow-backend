package com.knowflow.backflow.service;

import com.knowflow.backflow.dto.CreateKnowledgeDraftFromTicketRequest;
import com.knowflow.backflow.dto.ReviewKnowledgeDraftRequest;
import com.knowflow.backflow.dto.UpdateKnowledgeDraftRequest;
import com.knowflow.backflow.vo.KnowledgeBaseOptionVO;
import com.knowflow.backflow.vo.KnowledgeDraftVO;
import com.knowflow.common.response.PageResponse;

import java.util.List;

public interface KnowledgeDraftService {

    KnowledgeDraftVO createFromTicket(Long ticketId, CreateKnowledgeDraftFromTicketRequest request);

    PageResponse<KnowledgeDraftVO> page(Integer pageNo, Integer pageSize, Long knowledgeBaseId, String status, String draftType);

    List<KnowledgeBaseOptionVO> listKnowledgeBaseOptions();

    KnowledgeDraftVO getById(Long id);

    KnowledgeDraftVO update(Long id, UpdateKnowledgeDraftRequest request);

    KnowledgeDraftVO approve(Long id, ReviewKnowledgeDraftRequest request);

    KnowledgeDraftVO reject(Long id, ReviewKnowledgeDraftRequest request);

    KnowledgeDraftVO publish(Long id);
}
