package com.knowflow.qa.evaluation.vo;

import com.knowflow.qa.vo.QueryVariantVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RetrievalEvalResultVO {

    private Long id;
    private Long runId;
    private Long caseId;
    private String questionText;
    private String expectedStatus;
    private String actualStatus;
    private Long expectedDocumentId;
    private String expectedDocumentName;
    private Long actualTopDocumentId;
    private String actualTopDocumentName;
    private Double topRecallScore;
    private Double topLexicalScore;
    private Double topVectorScore;
    private Integer hitRank;
    private Integer keywordHitCount;
    private Integer keywordTotalCount;
    private Boolean passed;
    private String failureReason;
    private List<RetrievalEvalHitVO> hits;
    private List<QueryVariantVO> queryVariants;
    private LocalDateTime createdAt;
}
