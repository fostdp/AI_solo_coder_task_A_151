package com.waterwheel.chaintransmission.events;

import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;

public class EfficiencyOptimizationCompletedEvent extends ApplicationEvent {

    private final Integer deviceId;
    private final OptimizationResultDTO result;
    private final OffsetDateTime completedTime;

    public EfficiencyOptimizationCompletedEvent(Object source, Integer deviceId, OptimizationResultDTO result) {
        super(source);
        this.deviceId = deviceId;
        this.result = result;
        this.completedTime = OffsetDateTime.now();
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public OptimizationResultDTO getResult() {
        return result;
    }

    public OffsetDateTime getCompletedTime() {
        return completedTime;
    }
}
