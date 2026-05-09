package com.knowflow.qa.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.vo.AdminQaRecordVO;
import com.knowflow.qa.vo.RetrievalDebugVO;
import com.knowflow.qa.vo.RetrievalRecordVO;

import java.util.List;

public interface AdminQaRecordService {

    PageResponse<AdminQaRecordVO> page(Integer pageNo,
                                       Integer pageSize,
                                       String keyword,
                                       String answerStatus,
                                       Boolean needHumanHandoff,
                                       Long knowledgeBaseId,
                                       Long sessionId);

    AdminQaRecordVO getById(Long id);

    List<RetrievalRecordVO> listSources(Long id);

    RetrievalDebugVO getRetrievalDebug(Long id);
}
