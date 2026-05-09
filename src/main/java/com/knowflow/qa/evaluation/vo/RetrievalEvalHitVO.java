package com.knowflow.qa.evaluation.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalEvalHitVO {

    private Long documentId;
    private Long chunkId;
    private String documentName;
    private String snippetText;
    private Double recallScore;
    private Double lexicalScore;
    private Double vectorScore;
    private String recallStrategy;
    private Integer rankNo;
}
