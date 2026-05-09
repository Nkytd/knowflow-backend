package com.knowflow.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentKnowledgeDraftSuggestionVO {

    private Long ticketId;
    private String ticketNo;
    private Long existingDraftId;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String draftType;
    private String title;
    private String questionText;
    private String answerText;
    private Double confidence;
    private List<String> reasons;
    private String nextAction;
    private Map<String, Object> createDraftArguments;
}
