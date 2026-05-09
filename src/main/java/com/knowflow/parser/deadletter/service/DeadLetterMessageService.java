package com.knowflow.parser.deadletter.service;

import com.knowflow.common.response.PageResponse;
import com.knowflow.parser.messaging.ParseTaskDispatchMessage;
import com.knowflow.parser.deadletter.vo.DeadLetterMessageVO;

public interface DeadLetterMessageService {

    String REPLAY_STATUS_READY = "READY";
    String REPLAY_STATUS_MANUAL_REQUIRED = "MANUAL_REQUIRED";
    String REPLAY_STATUS_AUTO_REPLAYED = "AUTO_REPLAYED";
    String REPLAY_STATUS_MANUAL_REPLAYED = "MANUAL_REPLAYED";
    String REPLAY_STATUS_RESOLVED = "RESOLVED";

    PageResponse<DeadLetterMessageVO> page(Integer pageNo,
                                          Integer pageSize,
                                          String replayStatus,
                                          String taskType,
                                          Long taskId,
                                          Long documentId,
                                          String keyword);

    DeadLetterMessageVO getById(Long id);

    void replay(Long id);

    void processAutoReplayBatch();

    void resolveByTaskId(Long taskId);

    void recordDeadLetter(ParseTaskDispatchMessage message, String sourceQueue, String sourceExchange, String routingKey);
}
