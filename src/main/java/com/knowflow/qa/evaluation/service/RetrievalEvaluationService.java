package com.knowflow.qa.evaluation.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.evaluation.dto.CreateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.dto.RunRetrievalEvaluationRequest;
import com.knowflow.qa.evaluation.dto.UpdateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.vo.RetrievalEvalCaseVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalResultVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalRunVO;

import java.util.List;

public interface RetrievalEvaluationService {

    RetrievalEvalCaseVO createCase(CreateRetrievalEvalCaseRequest request);

    PageResponse<RetrievalEvalCaseVO> pageCases(Integer pageNo,
                                                Integer pageSize,
                                                Long knowledgeBaseId,
                                                String keyword,
                                                Boolean enabled);

    RetrievalEvalCaseVO updateCase(Long id, UpdateRetrievalEvalCaseRequest request);

    void deleteCase(Long id);

    RetrievalEvalRunVO run(RunRetrievalEvaluationRequest request);

    PageResponse<RetrievalEvalRunVO> pageRuns(Integer pageNo, Integer pageSize, Long knowledgeBaseId);

    RetrievalEvalRunVO getRun(Long id);

    List<RetrievalEvalResultVO> listResults(Long runId);
}
