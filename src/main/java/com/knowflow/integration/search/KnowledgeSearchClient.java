package com.knowflow.integration.search;

import com.knowflow.integration.search.model.KnowledgeSearchHit;
import com.knowflow.integration.search.model.QueryVariantHit;

import java.util.List;

public interface KnowledgeSearchClient {

    List<KnowledgeSearchHit> search(Long tenantId, Long knowledgeBaseId, String query, int topK);

    default List<QueryVariantHit> explainQuery(String query) {
        return List.of();
    }
}

