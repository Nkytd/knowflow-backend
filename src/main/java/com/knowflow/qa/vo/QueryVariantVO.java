package com.knowflow.qa.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryVariantVO {

    private String text;
    private String normalizedText;
    private String source;
    private Double weight;
}
