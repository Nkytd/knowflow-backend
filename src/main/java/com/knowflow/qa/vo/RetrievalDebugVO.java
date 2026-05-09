package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetrievalDebugVO {

    private Long qaMessageId;
    private String questionText;
    private String answerStatus;
    private Double minRecallScore;
    private Double topRecallScore;
    private List<QueryVariantVO> queryVariants;
    private List<RetrievalRecordVO> chunks;
}
