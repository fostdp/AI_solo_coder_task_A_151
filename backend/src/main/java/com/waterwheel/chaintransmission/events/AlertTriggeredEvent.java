package com.waterwheel.chaintransmission.events;

import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;
import java.util.Map;

public class AlertTriggeredEvent extends ApplicationEvent {

    private final Integer deviceId;
    private final String alertType;
    private final String alertLevel;
    private final String message;
    private final Map<String, Object> sensorData;
    private final OffsetDateTime triggeredTime;

    public AlertTriggeredEvent(Object source, Integer deviceId, String alertType,
                                String alertLevel, String message, Map<String, Object> sensorData) {
        super(source);
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.alertLevel = alertLevel;
        this.message = message;
        this.sensorData = sensorData;
        this.triggeredTime = OffsetDateTime.now();
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getSensorData() {
        return sensorData;
    }

    public OffsetDateTime getTriggeredTime() {
        return triggeredTime;
    }
}
