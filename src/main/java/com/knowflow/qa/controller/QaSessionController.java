package com.knowflow.qa.controller;

import com.knowflow.common.response.ApiResponse;
import com.knowflow.common.response.PageResponse;
import com.knowflow.qa.dto.CreateQaSessionRequest;
import com.knowflow.qa.service.QaSessionService;
import com.knowflow.qa.vo.QaSessionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/qa/sessions")
public class QaSessionController {

    private final QaSessionService qaSessionService;

    public QaSessionController(QaSessionService qaSessionService) {
        this.qaSessionService = qaSessionService;
    }

    @PostMapping
    public ApiResponse<QaSessionVO> create(@Valid @RequestBody CreateQaSessionRequest request) {
        return ApiResponse.success(qaSessionService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<QaSessionVO>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(qaSessionService.page(pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<QaSessionVO> detail(@PathVariable Long id) {
        return ApiResponse.success(qaSessionService.getById(id));
    }
}

