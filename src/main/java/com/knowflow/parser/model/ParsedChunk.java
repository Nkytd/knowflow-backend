package com.knowflow.parser.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedChunk {

    private Integer chunkNo;
    private String content;
    private Integer charCount;
    private Integer tokenCount;
}

