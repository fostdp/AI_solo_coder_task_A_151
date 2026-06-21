package com.waterwheel.chaintransmission.events;

import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;

public class ChainSimulationCompletedEvent extends ApplicationEvent {

    private final Integer deviceId;
    private final ChainDynamicsResultDTO result;
    private final OffsetDateTime completedTime;

    public ChainSimulationCompletedEvent(Object source, Integer deviceId, ChainDynamicsResultDTO result) {
        super(source);
        this.deviceId = deviceId;
        this.result = result;
        this.completedTime = OffsetDateTime.now();
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public ChainDynamicsResultDTO getResult() {
        return result;
    }

    public OffsetDateTime getCompletedTime() {
        return completedTime;
    }
}
