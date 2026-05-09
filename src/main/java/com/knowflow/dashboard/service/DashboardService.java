package com.knowflow.dashboard.service;

import com.knowflow.dashboard.vo.DashboardOverviewVO;
import com.knowflow.dashboard.vo.DashboardQuestionDetailVO;
import com.knowflow.dashboard.vo.DashboardTrendPointVO;
import com.knowflow.dashboard.vo.HotQuestionVO;
import com.knowflow.dashboard.vo.NoHitQuestionAnalysisVO;

import java.util.List;

public interface DashboardService {

    DashboardOverviewVO overview();

    List<DashboardTrendPointVO> trends(Integer days);

    List<HotQuestionVO> hotQuestions(Integer days, Integer limit);

    List<NoHitQuestionAnalysisVO> noHitQuestions(Integer days, Integer limit);

    DashboardQuestionDetailVO questionDetail(Integer days, Long knowledgeBaseId, String questionText);
}
