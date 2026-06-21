package com.waterwheel.chaintransmission.alarm_mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "alert")
public class AlertThresholdProperties {

    private long checkIntervalMs = 30000;
    private double chainTensionWarningRatio = 0.75;
    private double chainTensionCriticalRatio = 0.90;
    private double waterFlowMinRatio = 0.6;
    private long dedupWindowMs = 300_000;
}
