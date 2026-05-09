package com.knowflow.parser.service;

import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.parser.model.ParsedDocument;

public interface DocumentParsingService {

    ParsedDocument parse(KnowledgeDocumentEntity documentEntity);
}

