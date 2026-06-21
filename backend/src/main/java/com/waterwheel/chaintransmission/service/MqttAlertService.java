package com.waterwheel.chaintransmission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MqttAlertService {

    @Autowired
    private MqttPahoMessageHandler mqttOutbound;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mqtt.topic.alert}")
    private String alertTopic;

    public void publishAlert(Integer deviceId, String alertType, String alertLevel,
                              String message, Map<String, Object> sensorData) {
        try {
            Map<String, Object> alertPayload = new HashMap<>();
            alertPayload.put("deviceId", deviceId);
            alertPayload.put("alertType", alertType);
            alertPayload.put("alertLevel", alertLevel);
            alertPayload.put("message", message);
            alertPayload.put("sensorData", sensorData);
            alertPayload.put("timestamp", OffsetDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(alertPayload);

            Message<String> mqttMessage = MessageBuilder
                    .withPayload(jsonPayload)
                    .setHeader("mqtt_topic", alertTopic + "/" + deviceId)
                    .build();

            mqttOutbound.handleMessage(mqttMessage);
            log.info("MQTT告警发布成功: deviceId={}, type={}, level={}", deviceId, alertType, alertLevel);

        } catch (Exception e) {
            log.error("MQTT告警发布失败: {}", e.getMessage(), e);
        }
    }

    public void publishSensorData(Integer deviceId, Map<String, Object> sensorData) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceId", deviceId);
            payload.put("data", sensorData);
            payload.put("timestamp", OffsetDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payload);

            Message<String> mqttMessage = MessageBuilder
                    .withPayload(jsonPayload)
                    .setHeader("mqtt_topic", "waterwheel/sensor/" + deviceId)
                    .build();

            mqttOutbound.handleMessage(mqttMessage);

        } catch (Exception e) {
            log.error("MQTT传感器数据发布失败: {}", e.getMessage(), e);
        }
    }
}
