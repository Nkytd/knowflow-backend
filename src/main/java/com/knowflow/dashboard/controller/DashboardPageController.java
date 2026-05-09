package com.knowflow.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardPageController {

    @GetMapping("/")
    public String indexPage() {
        return "redirect:/assistant";
    }

    @GetMapping({"/workbench", "/assistant"})
    public String workbenchPage() {
        return "forward:/console/workbench.html";
    }

    @GetMapping("/admin/profile")
    public String profilePage() {
        return "forward:/console/profile.html";
    }

    @GetMapping("/admin/dashboard")
    public String dashboardPage() {
        return "forward:/console/dashboard.html";
    }

    @GetMapping("/admin/knowledge-bases")
    public String knowledgeBasePage() {
        return "forward:/console/knowledge-bases.html";
    }

    @GetMapping("/admin/documents")
    public String documentPage() {
        return "forward:/console/documents.html";
    }

    @GetMapping("/admin/parse-tasks")
    public String parseTaskPage() {
        return "forward:/console/parse-tasks.html";
    }

    @GetMapping("/admin/tickets")
    public String ticketPage() {
        return "forward:/console/tickets.html";
    }

    @GetMapping("/admin/knowledge-drafts")
    public String knowledgeDraftPage() {
        return "forward:/console/knowledge-drafts.html";
    }

    @GetMapping("/admin/qa-records")
    public String qaRecordPage() {
        return "forward:/console/qa-records.html";
    }

    @GetMapping("/admin/retrieval-evaluations")
    public String retrievalEvaluationPage() {
        return "forward:/console/retrieval-evaluations.html";
    }

    @GetMapping("/admin/audit-logs")
    public String auditLogPage() {
        return "forward:/console/audit-logs.html";
    }

    @GetMapping("/admin/ops-health")
    public String opsHealthPage() {
        return "forward:/console/ops-health.html";
    }

    @GetMapping("/admin/dead-letters")
    public String deadLetterPage() {
        return "forward:/console/dead-letters.html";
    }
}



