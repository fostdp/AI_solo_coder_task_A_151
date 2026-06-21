package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class AlertDTO {

    private Long alertId;
    private Integer deviceId;
    private OffsetDateTime alertTime;
    private String alertType;
    private String alertLevel;
    private String alertMessage;
    private Map<String, Object> sensorData;
    private String mqttTopic;
    private Boolean acknowledged;
    private OffsetDateTime acknowledgedTime;
}
