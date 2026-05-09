package com.knowflow.qa.evaluation.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RetrievalEvalRunVO {

    private Long id;
    private String runNo;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private Double passRate;
    private Double recallAtK;
    private Double top1HitRate;
    private Double noHitAccuracy;
    private Double avgTopScore;
    private Double avgTopLexicalScore;
    private Double avgTopVectorScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private List<RetrievalEvalResultVO> results;
}
