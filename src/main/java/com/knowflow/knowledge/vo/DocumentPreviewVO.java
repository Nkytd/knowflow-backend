package com.knowflow.knowledge.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentPreviewVO {

    private Long documentId;
    private String docName;
    private String contentType;
    private Boolean previewable;
    private Boolean truncated;
    private String previewText;
    private String message;
}
