package com.waterwheel.chaintransmission.alarm_mqtt.service;

import com.waterwheel.chaintransmission.alarm_mqtt.config.AlertThresholdProperties;
import com.waterwheel.chaintransmission.dto.AlertDTO;
import com.waterwheel.chaintransmission.entity.AlertRecord;
import com.waterwheel.chaintransmission.entity.ChainLinkParams;
import com.waterwheel.chaintransmission.entity.DeviceConfig;
import com.waterwheel.chaintransmission.entity.SensorData;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.events.AlertTriggeredEvent;
import com.waterwheel.chaintransmission.repository.AlertRecordRepository;
import com.waterwheel.chaintransmission.repository.ChainLinkParamsRepository;
import com.waterwheel.chaintransmission.repository.DeviceConfigRepository;
import com.waterwheel.chaintransmission.repository.SensorDataRepository;
import com.waterwheel.chaintransmission.service.MqttAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AlarmService {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ChainLinkParamsRepository chainLinkParamsRepository;

    @Autowired
    private DeviceConfigRepository deviceConfigRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    @Autowired
    private MqttAlertService mqttAlertService;

    @Autowired
    private AlertThresholdProperties thresholdProperties;

    @Autowired
    private com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${mqtt.topic.alert}")
    private String alertTopic;

    private final Map<String, Long> lastAlertTimestamps = new ConcurrentHashMap<>();

    @Scheduled(fixedRateString = "${alert.check-interval-ms:30000}")
    public void checkAndTriggerAlerts() {
        log.debug("开始定时告警检查");
        List<WaterwheelDevice> devices = findActiveDevices();
        for (WaterwheelDevice device : devices) {
            try {
                checkDeviceAlerts(device);
            } catch (Exception e) {
                log.error("设备告警检查失败, deviceId={}: {}", device.getDeviceId(), e.getMessage());
            }
        }
    }

    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        String dedupKey = event.getDeviceId() + "_" + event.getAlertType() + "_" + event.getAlertLevel();
        Long lastTime = lastAlertTimestamps.get(dedupKey);
        long now = System.currentTimeMillis();

        if (lastTime != null && (now - lastTime) < thresholdProperties.getDedupWindowMs()) {
            log.debug("告警去抖跳过, deviceId={}, type={}", event.getDeviceId(), event.getAlertType());
            return;
        }
        lastAlertTimestamps.put(dedupKey, now);

        AlertRecord record = new AlertRecord();
        record.setDeviceId(event.getDeviceId());
        record.setAlertTime(event.getTriggeredTime());
        record.setAlertType(event.getAlertType());
        record.setAlertLevel(event.getAlertLevel());
        record.setAlertMessage(event.getMessage());
        record.setSensorData(event.getSensorData());
        record.setMqttTopic(alertTopic + "/" + event.getDeviceId());
        record.setAcknowledged(false);
        alertRecordRepository.save(record);

        mqttAlertService.publishAlert(event.getDeviceId(), event.getAlertType(),
                event.getAlertLevel(), event.getMessage(), event.getSensorData());

        log.info("告警推送完成, deviceId={}, type={}, level={}",
                event.getDeviceId(), event.getAlertType(), event.getAlertLevel());
    }

    public AlertDTO triggerAlert(Integer deviceId, String alertType, String alertLevel,
                                 String message, Map<String, Object> sensorData) {
        AlertTriggeredEvent event = new AlertTriggeredEvent(
                this, deviceId, alertType, alertLevel, message, sensorData);
        eventPublisher.publishEvent(event);
        return buildAlertDTO(deviceId, alertType, alertLevel, message, sensorData);
    }

    private void checkDeviceAlerts(WaterwheelDevice device) {
        Integer deviceId = device.getDeviceId();
        Optional<SensorData> latestDataOpt = sensorDataRepository.findLatestByDeviceId(deviceId);
        if (latestDataOpt.isEmpty()) return;

        SensorData latest = latestDataOpt.get();
        Map<String, Object> sensorDataMap = buildSensorDataMap(latest);

        checkChainTensionAlert(deviceId, latest, sensorDataMap);
        checkWaterFlowAlert(deviceId, latest, sensorDataMap);
        checkVibrationAlert(deviceId, latest, sensorDataMap);
    }

    private void checkChainTensionAlert(Integer deviceId, SensorData latest, Map<String, Object> sensorDataMap) {
        if (latest.getChainTension() == null) return;
        Optional<ChainLinkParams> paramsOpt = chainLinkParamsRepository.findByDeviceId(deviceId);
        if (paramsOpt.isEmpty()) return;

        double allowableTension = paramsOpt.get().getAllowableTension().doubleValue();
        double currentTension = latest.getChainTension().doubleValue();
        double ratio = currentTension / allowableTension;

        if (ratio >= thresholdProperties.getChainTensionCriticalRatio()) {
            eventPublisher.publishEvent(new AlertTriggeredEvent(this, deviceId,
                    "CHAIN_TENSION_CRITICAL", "CRITICAL",
                    String.format("链条张力严重超限! 当前: %.1fN, 许用: %.1fN, 比值: %.2f%%.",
                            currentTension, allowableTension, ratio * 100),
                    sensorDataMap));
        } else if (ratio >= thresholdProperties.getChainTensionWarningRatio()) {
            eventPublisher.publishEvent(new AlertTriggeredEvent(this, deviceId,
                    "CHAIN_TENSION_WARNING", "WARNING",
                    String.format("链条张力偏高, 当前: %.1fN, 许用: %.1fN, 比值: %.2f%%",
                            currentTension, allowableTension, ratio * 100),
                    sensorDataMap));
        }
    }

    private void checkWaterFlowAlert(Integer deviceId, SensorData latest, Map<String, Object> sensorDataMap) {
        if (latest.getWaterFlow() == null) return;
        Optional<DeviceConfig> minFlowConfig = deviceConfigRepository
                .findByDeviceIdAndParamName(deviceId, "water_flow_min_threshold");
        double minFlow = minFlowConfig.map(c -> c.getParamValue().doubleValue()).orElse(500.0);
        double currentFlow = latest.getWaterFlow().doubleValue();

        if (currentFlow < minFlow * thresholdProperties.getWaterFlowMinRatio()) {
            eventPublisher.publishEvent(new AlertTriggeredEvent(this, deviceId,
                    "WATER_FLOW_LOW", "WARNING",
                    String.format("提水量过低! 当前: %.1f L/h, 阈值: %.1f L/h", currentFlow, minFlow),
                    sensorDataMap));
        }
    }

    private void checkVibrationAlert(Integer deviceId, SensorData latest, Map<String, Object> sensorDataMap) {
        if (latest.getVibrationAmplitude() == null) return;
        Optional<DeviceConfig> vibConfig = deviceConfigRepository
                .findByDeviceIdAndParamName(deviceId, "vibration_warning_threshold");
        double threshold = vibConfig.map(c -> c.getParamValue().doubleValue()).orElse(2.5);
        double current = latest.getVibrationAmplitude().doubleValue();

        if (current > threshold) {
            eventPublisher.publishEvent(new AlertTriggeredEvent(this, deviceId,
                    "EXCESSIVE_VIBRATION", "WARNING",
                    String.format("振动幅度过大! 当前: %.4f mm, 阈值: %.2f mm", current, threshold),
                    sensorDataMap));
        }
    }

    private Map<String, Object> buildSensorDataMap(SensorData data) {
        Map<String, Object> map = new HashMap<>();
        map.put("time", data.getTime().toString());
        if (data.getSprocketSpeed() != null) map.put("sprocketSpeed", data.getSprocketSpeed().doubleValue());
        if (data.getScraperLoad() != null) map.put("scraperLoad", data.getScraperLoad().doubleValue());
        if (data.getChainTension() != null) map.put("chainTension", data.getChainTension().doubleValue());
        if (data.getWaterFlow() != null) map.put("waterFlow", data.getWaterFlow().doubleValue());
        if (data.getVibrationAmplitude() != null) map.put("vibrationAmplitude", data.getVibrationAmplitude().doubleValue());
        if (data.getTorque() != null) map.put("torque", data.getTorque().doubleValue());
        return map;
    }

    private AlertDTO buildAlertDTO(Integer deviceId, String alertType, String alertLevel,
                                   String message, Map<String, Object> sensorData) {
        AlertDTO dto = new AlertDTO();
        dto.setDeviceId(deviceId);
        dto.setAlertTime(OffsetDateTime.now());
        dto.setAlertType(alertType);
        dto.setAlertLevel(alertLevel);
        dto.setAlertMessage(message);
        dto.setSensorData(sensorData);
        dto.setMqttTopic(alertTopic + "/" + deviceId);
        dto.setAcknowledged(false);
        return dto;
    }

    private List<WaterwheelDevice> findActiveDevices() {
        return deviceRepository.findByStatus("ACTIVE");
    }
}
