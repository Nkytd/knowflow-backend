package com.knowflow.integration.search.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryVariantHit {

    private String text;
    private String normalizedText;
    private String source;
    private Double weight;
}
