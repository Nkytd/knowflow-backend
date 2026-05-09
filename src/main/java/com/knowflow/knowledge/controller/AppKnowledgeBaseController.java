package com.knowflow.knowledge.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.knowledge.vo.KnowledgeBaseOptionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/app/knowledge-bases")
public class AppKnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public AppKnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/options")
    public ApiResponse<List<KnowledgeBaseOptionVO>> options() {
        return ApiResponse.success(knowledgeBaseService.listEnabledOptions());
    }
}
