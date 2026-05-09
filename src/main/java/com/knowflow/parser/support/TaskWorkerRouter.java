package com.knowflow.parser.support;

import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.worker.DocumentParseWorker;
import com.knowflow.parser.worker.KnowledgeIndexWorker;
import org.springframework.stereotype.Component;

@Component
public class TaskWorkerRouter {

    public static final String TASK_TYPE_PARSE = "PARSE";
    public static final String TASK_TYPE_INDEX_VECTOR = "INDEX_VECTOR";

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentParseWorker documentParseWorker;
    private final KnowledgeIndexWorker knowledgeIndexWorker;

    public TaskWorkerRouter(ParseTaskMapper parseTaskMapper,
                            DocumentParseWorker documentParseWorker,
                            KnowledgeIndexWorker knowledgeIndexWorker) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentParseWorker = documentParseWorker;
        this.knowledgeIndexWorker = knowledgeIndexWorker;
    }

    public void process(Long taskId, boolean deadLetterOnFailure) {
        ParseTaskEntity task = parseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Task not found");
        }
        if (TASK_TYPE_PARSE.equals(task.getTaskType())) {
            documentParseWorker.process(taskId, deadLetterOnFailure);
            return;
        }
        if (TASK_TYPE_INDEX_VECTOR.equals(task.getTaskType())) {
            knowledgeIndexWorker.process(taskId, deadLetterOnFailure);
            return;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported task type: " + task.getTaskType());
    }
}
