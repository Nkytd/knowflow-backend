package com.knowflow.backflow.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeBaseOptionVO {

    private Long id;
    private String kbCode;
    private String kbName;
    private String status;
    private Integer docCount;
}
