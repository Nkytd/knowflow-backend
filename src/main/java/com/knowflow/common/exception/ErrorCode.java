package com.knowflow.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "OK"),
    BAD_REQUEST(40001, "请求参数错误"),
    UNAUTHORIZED(40101, "未登录或登录已失效"),
    FORBIDDEN(40301, "无权限访问"),
    NOT_FOUND(40401, "数据不存在"),
    CONFLICT(40901, "状态冲突或重复操作"),
    MODEL_CALL_FAILED(51001, "模型调用失败"),
    PARSE_TASK_FAILED(52001, "文档解析失败"),
    SEARCH_RESULT_INSUFFICIENT(53001, "检索结果不足"),
    SYSTEM_ERROR(50001, "系统异常");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

