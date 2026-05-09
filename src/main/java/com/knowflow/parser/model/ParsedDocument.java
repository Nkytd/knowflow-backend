package com.knowflow.parser.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParsedDocument {

    private String normalizedText;
    private List<ParsedChunk> chunks;
}

