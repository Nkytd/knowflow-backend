package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentBatchOperationVO {

    private String action;
    private Integer affectedCount;
    private List<Long> documentIds;
}
