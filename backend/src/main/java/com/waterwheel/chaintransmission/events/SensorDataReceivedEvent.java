package com.waterwheel.chaintransmission.events;

import com.waterwheel.chaintransmission.dto.SensorDataDTO;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;

public class SensorDataReceivedEvent extends ApplicationEvent {

    private final Integer deviceId;
    private final SensorDataDTO sensorData;
    private final OffsetDateTime receivedTime;

    public SensorDataReceivedEvent(Object source, Integer deviceId, SensorDataDTO sensorData) {
        super(source);
        this.deviceId = deviceId;
        this.sensorData = sensorData;
        this.receivedTime = OffsetDateTime.now();
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public SensorDataDTO getSensorData() {
        return sensorData;
    }

    public OffsetDateTime getReceivedTime() {
        return receivedTime;
    }
}
