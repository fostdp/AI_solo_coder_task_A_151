package com.waterwheel.chaintransmission.service.impl;

import com.waterwheel.chaintransmission.dto.AlertDTO;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.dto.SensorDataDTO;
import com.waterwheel.chaintransmission.entity.*;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.*;
import com.waterwheel.chaintransmission.service.MqttAlertService;
import com.waterwheel.chaintransmission.service.WaterwheelService;
import com.waterwheel.chaintransmission.simulation.ChainDynamicsSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    private ChainDynamicsSimulator chainDynamicsSimulator;

    @Autowired
    private WaterEfficiencyOptimizer waterEfficiencyOptimizer;

    @Autowired
    private MqttAlertService mqttAlertService;

    @Value("${mqtt.topic.alert}")
    private String alertTopic;

    @Value("${alert.chain-tension-warning-ratio:0.75}")
    private double tensionWarningRatio;

    @Value("${alert.chain-tension-critical-ratio:0.90}")
    private double tensionCriticalRatio;

    @Value("${alert.water-flow-min-ratio:0.6}")
    private double waterFlowMinRatio;

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
        data.setTorqueUnit(dto.getTorqueUnit() != null ? dto.getTorqueUnit() : "N·m");
        return sensorDataRepository.save(data);
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

        ChainLinkParams params = chainLinkParamsRepository.findByDeviceId(deviceId)
                .orElseGet(() -> getDefaultChainLinkParams(deviceId));

        ChainDynamicsResultDTO result = chainDynamicsSimulator.simulate(device, params, inputSpeedRPM, inputTorque);

        ChainDynamicsSimulation entity = new ChainDynamicsSimulation();
        entity.setDeviceId(deviceId);
        entity.setSimulationTime(OffsetDateTime.now());
        entity.setInputSpeed(result.getInputSpeed());
        entity.setInputTorque(result.getInputTorque());
        entity.setLinkCount(result.getLinkCount());
        entity.setTensionDistribution(Map.of("data", result.getTensionDistribution()));
        entity.setVibrationFrequencies(Map.of("data", result.getVibrationFrequencies()));
        entity.setCollisionForces(Map.of("data", result.getCollisionForces()));
        entity.setMaxTension(result.getMaxTension());
        entity.setMinTension(result.getMinTension());
        entity.setAvgTension(result.getAvgTension());
        entity.setResonanceRisk(result.getResonanceRisk());
        entity.setSimulationDurationMs(result.getSimulationDurationMs());
        simulationRepository.save(entity);

        if (Boolean.TRUE.equals(result.getResonanceRisk())) {
            Map<String, Object> sensorData = new HashMap<>();
            sensorData.put("maxTension", result.getMaxTension());
            sensorData.put("resonanceFrequencies", result.getVibrationFrequencies());
            triggerAlert(deviceId, "RESONANCE_RISK", "WARNING",
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

        OptimizationResultDTO result = waterEfficiencyOptimizer.optimize(device, currentWaterFlow);

        EfficiencyOptimization entity = new EfficiencyOptimization();
        entity.setDeviceId(deviceId);
        entity.setOptimizationTime(OffsetDateTime.now());
        entity.setMethod(result.getMethod());
        entity.setScraperShapeParams(Map.of(
                "minDepth", 0.05, "maxDepth", 0.20,
                "minWidth", 0.10, "maxWidth", 0.40,
                "minAngle", 15.0, "maxAngle", 60.0
        ));
        entity.setChainSpeedRange(Map.of("min", 0.5, "max", 3.0));
        entity.setOptimalScraperDepth(result.getOptimalScraperDepth());
        entity.setOptimalScraperWidth(result.getOptimalScraperWidth());
        entity.setOptimalScraperAngle(result.getOptimalScraperAngle());
        entity.setOptimalChainSpeed(result.getOptimalChainSpeed());
        entity.setPredictedMaxWaterFlow(result.getPredictedMaxWaterFlow());
        entity.setEfficiencyImprovement(result.getEfficiencyImprovement());
        entity.setResponseSurfaceData(Map.of(
                "equation", result.getResponseSurfaceEquation(),
                "designPoints", result.getDesignPoints()
        ));
        entity.setIterations(result.getIterations());
        entity.setConvergence(result.getConvergence());
        optimizationRepository.save(entity);

        return result;
    }

    @Override
    public List<EfficiencyOptimization> getOptimizationHistory(Integer deviceId) {
        return optimizationRepository.findByDeviceIdOrderByOptimizationTimeDesc(deviceId);
    }

    @Override
    public AlertDTO triggerAlert(Integer deviceId, String alertType, String alertLevel,
                                  String message, Map<String, Object> sensorData) {
        AlertRecord record = new AlertRecord();
        record.setDeviceId(deviceId);
        record.setAlertTime(OffsetDateTime.now());
        record.setAlertType(alertType);
        record.setAlertLevel(alertLevel);
        record.setAlertMessage(message);
        record.setSensorData(sensorData);
        record.setMqttTopic(alertTopic + "/" + deviceId);
        record.setAcknowledged(false);
        alertRecordRepository.save(record);

        mqttAlertService.publishAlert(deviceId, alertType, alertLevel, message, sensorData);

        return convertToAlertDTO(record);
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

    @Override
    @Scheduled(fixedRateString = "${alert.check-interval-ms:30000}")
    public void checkAndTriggerAlerts() {
        log.debug("开始定时告警检查");
        List<WaterwheelDevice> devices = deviceRepository.findByStatus("ACTIVE");

        for (WaterwheelDevice device : devices) {
            try {
                checkDeviceAlerts(device);
            } catch (Exception e) {
                log.error("设备告警检查失败, deviceId={}: {}", device.getDeviceId(), e.getMessage());
            }
        }
    }

    private void checkDeviceAlerts(WaterwheelDevice device) {
        Integer deviceId = device.getDeviceId();
        Optional<SensorData> latestDataOpt = getLatestSensorData(deviceId);

        if (latestDataOpt.isEmpty()) {
            return;
        }

        SensorData latest = latestDataOpt.get();
        Map<String, Object> sensorDataMap = buildSensorDataMap(latest);

        Optional<ChainLinkParams> paramsOpt = chainLinkParamsRepository.findByDeviceId(deviceId);
        if (paramsOpt.isPresent() && latest.getChainTension() != null) {
            double allowableTension = paramsOpt.get().getAllowableTension().doubleValue();
            double currentTension = latest.getChainTension().doubleValue();
            double ratio = currentTension / allowableTension;

            if (ratio >= tensionCriticalRatio) {
                triggerAlert(deviceId, "CHAIN_TENSION_CRITICAL", "CRITICAL",
                        String.format("链条张力严重超限! 当前: %.1fN, 许用: %.1fN, 比值: %.2f%%. 存在断裂风险!",
                                currentTension, allowableTension, ratio * 100), sensorDataMap);
            } else if (ratio >= tensionWarningRatio) {
                triggerAlert(deviceId, "CHAIN_TENSION_WARNING", "WARNING",
                        String.format("链条张力偏高, 当前: %.1fN, 许用: %.1fN, 比值: %.2f%%",
                                currentTension, allowableTension, ratio * 100), sensorDataMap);
            }
        }

        if (latest.getWaterFlow() != null) {
            Optional<DeviceConfig> minFlowConfig = deviceConfigRepository
                    .findByDeviceIdAndParamName(deviceId, "water_flow_min_threshold");

            double minFlow = minFlowConfig.map(c -> c.getParamValue().doubleValue()).orElse(500.0);
            double currentFlow = latest.getWaterFlow().doubleValue();

            if (currentFlow < minFlow * waterFlowMinRatio) {
                triggerAlert(deviceId, "WATER_FLOW_LOW", "WARNING",
                        String.format("提水量过低! 当前: %.1f L/h, 阈值: %.1f L/h", currentFlow, minFlow),
                        sensorDataMap);
            }
        }

        if (latest.getVibrationAmplitude() != null) {
            Optional<DeviceConfig> vibConfig = deviceConfigRepository
                    .findByDeviceIdAndParamName(deviceId, "vibration_warning_threshold");
            double threshold = vibConfig.map(c -> c.getParamValue().doubleValue()).orElse(2.5);

            if (latest.getVibrationAmplitude().doubleValue() > threshold) {
                triggerAlert(deviceId, "EXCESSIVE_VIBRATION", "WARNING",
                        String.format("振动幅度过大! 当前: %.4f mm, 阈值: %.2f mm",
                                latest.getVibrationAmplitude().doubleValue(), threshold), sensorDataMap);
            }
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

    private ChainLinkParams getDefaultChainLinkParams(Integer deviceId) {
        ChainLinkParams params = new ChainLinkParams();
        params.setDeviceId(deviceId);
        params.setLinkMass(BigDecimal.valueOf(0.25));
        params.setLinkLength(BigDecimal.valueOf(0.125));
        params.setLinkStiffness(BigDecimal.valueOf(500000.0));
        params.setLinkDamping(BigDecimal.valueOf(150.0));
        params.setFrictionCoefficient(BigDecimal.valueOf(0.15));
        params.setAllowableTension(BigDecimal.valueOf(15000.0));
        params.setMaterial("锻铁");
        return chainLinkParamsRepository.save(params);
    }

    private double round(double value, int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return Math.round(value * factor) / factor;
    }
}
