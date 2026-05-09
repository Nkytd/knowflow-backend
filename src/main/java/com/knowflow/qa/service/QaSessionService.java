package com.knowflow.qa.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.dto.CreateQaSessionRequest;
import com.knowflow.qa.entity.QaSessionEntity;
import com.knowflow.qa.vo.QaSessionVO;

public interface QaSessionService {

    QaSessionVO create(CreateQaSessionRequest request);

    PageResponse<QaSessionVO> page(Integer pageNo, Integer pageSize);

    QaSessionVO getById(Long id);

    QaSessionEntity getEntityByIdForCurrentUser(Long id);

    void touchLastMessageAt(Long sessionId);
}

