package com.knowflow.integration.search.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSearchHit {

    private Long documentId;
    private Long chunkId;
    private String documentName;
    private String snippetText;
    private Double score;
    private Double lexicalScore;
    private Double vectorScore;
    private String recallStrategy;
    private Integer rankNo;
}
