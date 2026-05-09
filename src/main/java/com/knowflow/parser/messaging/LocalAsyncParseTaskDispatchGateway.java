package com.knowflow.parser.messaging;

import com.knowflow.parser.support.TaskWorkerRouter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "local", matchIfMissing = true)
public class LocalAsyncParseTaskDispatchGateway implements ParseTaskDispatchGateway {

    private final TaskWorkerRouter taskWorkerRouter;

    public LocalAsyncParseTaskDispatchGateway(TaskWorkerRouter taskWorkerRouter) {
        this.taskWorkerRouter = taskWorkerRouter;
    }

    @Override
    @Async("applicationTaskExecutor")
    public void dispatch(Long taskId) {
        taskWorkerRouter.process(taskId, false);
    }
}
