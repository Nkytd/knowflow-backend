package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalRecordVO {

    private Long id;
    private Long documentId;
    private Long chunkId;
    private String documentName;
    private Double recallScore;
    private Double lexicalScore;
    private Double vectorScore;
    private String recallStrategy;
    private Integer rankNo;
    private String snippetText;
}
