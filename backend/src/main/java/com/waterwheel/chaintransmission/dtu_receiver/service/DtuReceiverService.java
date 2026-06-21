package com.waterwheel.chaintransmission.dtu_receiver.service;

import com.waterwheel.chaintransmission.dto.SensorDataDTO;
import com.waterwheel.chaintransmission.dtu_receiver.config.DtuReceiverProperties;
import com.waterwheel.chaintransmission.entity.SensorData;
import com.waterwheel.chaintransmission.events.SensorDataReceivedEvent;
import com.waterwheel.chaintransmission.repository.SensorDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DtuReceiverService {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DtuReceiverProperties properties;

    public SensorData receiveAndSave(SensorDataDTO dto) {
        List<String> errors = validate(dto);
        if (!errors.isEmpty()) {
            log.warn("传感器数据校验失败, deviceId={}, errors={}", dto.getDeviceId(), errors);
            throw new IllegalArgumentException("传感器数据校验失败: " + String.join("; ", errors));
        }

        SensorData entity = convertToEntity(dto);
        SensorData saved = sensorDataRepository.save(entity);

        if (properties.isPublishEvents()) {
            eventPublisher.publishEvent(new SensorDataReceivedEvent(this, dto.getDeviceId(), dto));
            log.debug("发布 SensorDataReceivedEvent, deviceId={}", dto.getDeviceId());
        }
        return saved;
    }

    public Map<String, Integer> receiveBatch(List<SensorDataDTO> dtos) {
        int success = 0, failed = 0;
        for (SensorDataDTO dto : dtos) {
            try {
                receiveAndSave(dto);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("批量保存传感器数据失败, deviceId={}: {}", dto.getDeviceId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("total", dtos.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    public List<String> validate(SensorDataDTO dto) {
        List<String> errors = new ArrayList<>();
        if (!properties.isValidationEnabled()) return errors;

        if (dto.getDeviceId() == null || dto.getDeviceId() <= 0) {
            errors.add("deviceId 非法");
        }
        if (dto.getSprocketSpeed() != null) {
            double v = dto.getSprocketSpeed().doubleValue();
            if (v < properties.getMinSprocketSpeed() || v > properties.getMaxSprocketSpeed()) {
                errors.add(String.format("链轮转速 %.2f 超出范围 [%.0f, %.0f]",
                        v, properties.getMinSprocketSpeed(), properties.getMaxSprocketSpeed()));
            }
        }
        if (dto.getChainTension() != null) {
            double v = dto.getChainTension().doubleValue();
            if (v < properties.getMinChainTension() || v > properties.getMaxChainTension()) {
                errors.add(String.format("链条张力 %.2f 超出范围 [%.0f, %.0f]",
                        v, properties.getMinChainTension(), properties.getMaxChainTension()));
            }
        }
        if (dto.getWaterFlow() != null) {
            double v = dto.getWaterFlow().doubleValue();
            if (v < properties.getMinWaterFlow() || v > properties.getMaxWaterFlow()) {
                errors.add(String.format("提水量 %.2f 超出范围 [%.0f, %.0f]",
                        v, properties.getMinWaterFlow(), properties.getMaxWaterFlow()));
            }
        }
        if (dto.getScraperLoad() != null) {
            double v = dto.getScraperLoad().doubleValue();
            if (v < properties.getMinScraperLoad() || v > properties.getMaxScraperLoad()) {
                errors.add(String.format("刮板载荷 %.2f 超出范围 [%.0f, %.0f]",
                        v, properties.getMinScraperLoad(), properties.getMaxScraperLoad()));
            }
        }
        return errors;
    }

    private SensorData convertToEntity(SensorDataDTO dto) {
        SensorData data = new SensorData();
        data.setTime(dto.getTime() != null ? dto.getTime() : OffsetDateTime.now());
        data.setDeviceId(dto.getDeviceId());
        data.setSprocketSpeed(dto.getSprocketSpeed());
        data.setSprocketSpeedUnit(dto.getSprocketSpeedUnit() != null ? dto.getSprocketSpeedUnit() : "RPM");
        data.setScraperLoad(dto.getScraperLoad());
        data.setScraperLoadUnit(dto.getScraperLoadUnit() != null ? dto.getScraperLoadUnit() : "N");
        data.setChainTension(dto.getChainTension());
        data.setChainTensionUnit(dto.getChainTensionUnit() != null ? dto.getChainTensionUnit() : "N");
        data.setWaterFlow(dto.getWaterFlow());
        data.setWaterFlowUnit(dto.getWaterFlowUnit() != null ? dto.getWaterFlowUnit() : "L/h");
        data.setVibrationAmplitude(dto.getVibrationAmplitude());
        data.setChainElongation(dto.getChainElongation());
        data.setTorque(dto.getTorque());
        data.setTorqueUnit(dto.getTorqueUnit() != null ? dto.getTorqueUnit() : "N\u00b7m");
        return data;
    }
}
