package com.knowflow.ops.service;

import com.knowflow.ops.vo.InfrastructureHealthVO;
import com.knowflow.ops.vo.AiUsageOverviewVO;
import com.knowflow.ops.vo.TaskOpsOverviewVO;

public interface OpsMetricsService {

    TaskOpsOverviewVO taskOverview(Integer days);

    TaskOpsOverviewVO globalTaskOverview(Integer days);

    InfrastructureHealthVO infrastructureHealth();

    AiUsageOverviewVO aiUsage(Integer days);
}
