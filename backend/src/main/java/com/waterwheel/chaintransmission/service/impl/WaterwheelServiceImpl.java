package com.waterwheel.chaintransmission.service.impl;

import com.waterwheel.chaintransmission.alarm_mqtt.service.AlarmService;
import com.waterwheel.chaintransmission.chain_simulator.service.ChainSimulatorService;
import com.waterwheel.chaintransmission.dto.AlertDTO;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.dto.SensorDataDTO;
import com.waterwheel.chaintransmission.dtu_receiver.service.DtuReceiverService;
import com.waterwheel.chaintransmission.efficiency_optimizer.service.EfficiencyOptimizerService;
import com.waterwheel.chaintransmission.entity.*;
import com.waterwheel.chaintransmission.repository.*;
import com.waterwheel.chaintransmission.service.WaterwheelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class WaterwheelServiceImpl implements WaterwheelService {

    @Autowired
    private WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private ChainDynamicsSimulationRepository simulationRepository;

    @Autowired
    private EfficiencyOptimizationRepository optimizationRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    @Autowired
    private DeviceConfigRepository deviceConfigRepository;

    @Autowired
    private ChainLinkParamsRepository chainLinkParamsRepository;

    @Autowired
    private DtuReceiverService dtuReceiverService;

    @Autowired
    private ChainSimulatorService chainSimulatorService;

    @Autowired
    private EfficiencyOptimizerService efficiencyOptimizerService;

    @Autowired
    private AlarmService alarmService;

    @Override
    public List<WaterwheelDevice> getAllDevices() {
        return deviceRepository.findAll();
    }

    @Override
    public Optional<WaterwheelDevice> getDeviceById(Integer deviceId) {
        return deviceRepository.findById(deviceId);
    }

    @Override
    public WaterwheelDevice saveDevice(WaterwheelDevice device) {
        return deviceRepository.save(device);
    }

    @Override
    public SensorData saveSensorData(SensorDataDTO dto) {
        return dtuReceiverService.receiveAndSave(dto);
    }

    @Override
    public List<SensorData> getSensorDataByDevice(Integer deviceId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return sensorDataRepository.findByDeviceIdAndTimeBetweenOrderByTimeAsc(deviceId, startTime, endTime);
    }

    @Override
    public Optional<SensorData> getLatestSensorData(Integer deviceId) {
        return sensorDataRepository.findLatestByDeviceId(deviceId);
    }

    @Override
    public List<SensorData> getRecentSensorData(Integer deviceId, int limit) {
        return sensorDataRepository.findRecentByDeviceId(deviceId, limit);
    }

    @Override
    public Map<String, Object> getSensorDataStatistics(Integer deviceId, int hours) {
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(hours);
        List<SensorData> dataList = getSensorDataByDevice(deviceId, startTime, OffsetDateTime.now());

        Map<String, Object> stats = new HashMap<>();
        if (dataList.isEmpty()) {
            stats.put("count", 0);
            return stats;
        }

        stats.put("count", dataList.size());
        stats.put("timeRange", Map.of("start", startTime, "end", OffsetDateTime.now()));

        stats.put("sprocketSpeed", calculateStats(dataList.stream()
                .map(SensorData::getSprocketSpeed).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).collect(Collectors.toList())));

        stats.put("chainTension", calculateStats(dataList.stream()
                .map(SensorData::getChainTension).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).collect(Collectors.toList())));

        stats.put("waterFlow", calculateStats(dataList.stream()
                .map(SensorData::getWaterFlow).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).collect(Collectors.toList())));

        stats.put("scraperLoad", calculateStats(dataList.stream()
                .map(SensorData::getScraperLoad).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).collect(Collectors.toList())));

        return stats;
    }

    private Map<String, Object> calculateStats(List<Double> values) {
        Map<String, Object> result = new HashMap<>();
        if (values.isEmpty()) {
            return result;
        }

        DoubleSummaryStatistics stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        result.put("min", round(stats.getMin(), 4));
        result.put("max", round(stats.getMax(), 4));
        result.put("avg", round(stats.getAverage(), 4));
        result.put("sum", round(stats.getSum(), 4));

        double mean = stats.getAverage();
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        result.put("stdDev", round(Math.sqrt(variance), 4));

        return result;
    }

    @Override
    public ChainDynamicsResultDTO runChainDynamicsSimulation(Integer deviceId, double inputSpeedRPM, double inputTorque) {
        WaterwheelDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));

        ChainDynamicsResultDTO result = chainSimulatorService.runSimulation(device, inputSpeedRPM, inputTorque);

        if (Boolean.TRUE.equals(result.getResonanceRisk())) {
            Map<String, Object> sensorData = new HashMap<>();
            sensorData.put("maxTension", result.getMaxTension());
            sensorData.put("resonanceFrequencies", result.getVibrationFrequencies());
            alarmService.triggerAlert(deviceId, "RESONANCE_RISK", "WARNING",
                    "链传动仿真检测到共振风险", sensorData);
        }

        return result;
    }

    @Override
    public List<ChainDynamicsSimulation> getSimulationHistory(Integer deviceId) {
        return simulationRepository.findByDeviceIdOrderBySimulationTimeDesc(deviceId);
    }

    @Override
    public OptimizationResultDTO runEfficiencyOptimization(Integer deviceId) {
        WaterwheelDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));

        double currentWaterFlow = 600.0;
        Optional<SensorData> latestData = getLatestSensorData(deviceId);
        if (latestData.isPresent() && latestData.get().getWaterFlow() != null) {
            currentWaterFlow = latestData.get().getWaterFlow().doubleValue();
        }

        return efficiencyOptimizerService.runOptimization(device, currentWaterFlow);
    }

    @Override
    public List<EfficiencyOptimization> getOptimizationHistory(Integer deviceId) {
        return optimizationRepository.findByDeviceIdOrderByOptimizationTimeDesc(deviceId);
    }

    @Override
    public AlertDTO triggerAlert(Integer deviceId, String alertType, String alertLevel,
                                  String message, Map<String, Object> sensorData) {
        return alarmService.triggerAlert(deviceId, alertType, alertLevel, message, sensorData);
    }

    @Override
    public List<AlertDTO> getAlertsByDevice(Integer deviceId) {
        return alertRecordRepository.findByDeviceIdOrderByAlertTimeDesc(deviceId)
                .stream().map(this::convertToAlertDTO).collect(Collectors.toList());
    }

    @Override
    public List<AlertDTO> getRecentAlerts(int hours) {
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(hours);
        return alertRecordRepository.findByAlertTimeBetweenOrderByAlertTimeDesc(startTime, OffsetDateTime.now())
                .stream().map(this::convertToAlertDTO).collect(Collectors.toList());
    }

    @Override
    public List<AlertDTO> getUnacknowledgedAlerts(int limit) {
        return alertRecordRepository.findUnacknowledged(limit)
                .stream().map(this::convertToAlertDTO).collect(Collectors.toList());
    }

    @Override
    public AlertDTO acknowledgeAlert(Long alertId) {
        AlertRecord record = alertRecordRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + alertId));
        record.setAcknowledged(true);
        record.setAcknowledgedTime(OffsetDateTime.now());
        alertRecordRepository.save(record);
        return convertToAlertDTO(record);
    }

    @Override
    public List<DeviceConfig> getDeviceConfigs(Integer deviceId) {
        return deviceConfigRepository.findByDeviceId(deviceId);
    }

    @Override
    public DeviceConfig updateDeviceConfig(DeviceConfig config) {
        config.setUpdatedTime(OffsetDateTime.now());
        return deviceConfigRepository.save(config);
    }

    @Override
    public Optional<ChainLinkParams> getChainLinkParams(Integer deviceId) {
        return chainLinkParamsRepository.findByDeviceId(deviceId);
    }

    @Override
    public ChainLinkParams saveChainLinkParams(ChainLinkParams params) {
        return chainLinkParamsRepository.save(params);
    }

    private AlertDTO convertToAlertDTO(AlertRecord record) {
        AlertDTO dto = new AlertDTO();
        dto.setAlertId(record.getAlertId());
        dto.setDeviceId(record.getDeviceId());
        dto.setAlertTime(record.getAlertTime());
        dto.setAlertType(record.getAlertType());
        dto.setAlertLevel(record.getAlertLevel());
        dto.setAlertMessage(record.getAlertMessage());
        dto.setSensorData(record.getSensorData());
        dto.setMqttTopic(record.getMqttTopic());
        dto.setAcknowledged(record.getAcknowledged());
        dto.setAcknowledgedTime(record.getAcknowledgedTime());
        return dto;
    }

    private double round(double value, int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }
}
