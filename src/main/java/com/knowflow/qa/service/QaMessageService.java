package com.knowflow.qa.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.dto.AskQuestionRequest;
import com.knowflow.qa.dto.SubmitFeedbackRequest;
import com.knowflow.qa.vo.QaMessageVO;
import com.knowflow.qa.vo.RetrievalRecordVO;

import java.util.List;

public interface QaMessageService {

    QaMessageVO ask(AskQuestionRequest request);

    PageResponse<QaMessageVO> pageBySession(Long sessionId, Integer pageNo, Integer pageSize);

    QaMessageVO getById(Long id);

    List<RetrievalRecordVO> listSources(Long messageId);

    void submitFeedback(Long messageId, SubmitFeedbackRequest request);
}
