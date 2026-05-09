package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseOptionVO {

    private Long id;
    private String kbCode;
    private String kbName;
    private String description;
    private Integer docCount;
}
