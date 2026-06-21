package com.waterwheel.chaintransmission.service;

import com.waterwheel.chaintransmission.dto.AlertDTO;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.dto.SensorDataDTO;
import com.waterwheel.chaintransmission.entity.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WaterwheelService {

    List<WaterwheelDevice> getAllDevices();
    Optional<WaterwheelDevice> getDeviceById(Integer deviceId);
    WaterwheelDevice saveDevice(WaterwheelDevice device);

    SensorData saveSensorData(SensorDataDTO dto);
    List<SensorData> getSensorDataByDevice(Integer deviceId, OffsetDateTime startTime, OffsetDateTime endTime);
    Optional<SensorData> getLatestSensorData(Integer deviceId);
    List<SensorData> getRecentSensorData(Integer deviceId, int limit);
    Map<String, Object> getSensorDataStatistics(Integer deviceId, int hours);

    ChainDynamicsResultDTO runChainDynamicsSimulation(Integer deviceId, double inputSpeedRPM, double inputTorque);
    List<ChainDynamicsSimulation> getSimulationHistory(Integer deviceId);

    OptimizationResultDTO runEfficiencyOptimization(Integer deviceId);
    List<EfficiencyOptimization> getOptimizationHistory(Integer deviceId);

    AlertDTO triggerAlert(Integer deviceId, String alertType, String alertLevel, String message, Map<String, Object> sensorData);
    List<AlertDTO> getAlertsByDevice(Integer deviceId);
    List<AlertDTO> getRecentAlerts(int hours);
    List<AlertDTO> getUnacknowledgedAlerts(int limit);
    AlertDTO acknowledgeAlert(Long alertId);

    List<DeviceConfig> getDeviceConfigs(Integer deviceId);
    DeviceConfig updateDeviceConfig(DeviceConfig config);

    Optional<ChainLinkParams> getChainLinkParams(Integer deviceId);
    ChainLinkParams saveChainLinkParams(ChainLinkParams params);

    void checkAndTriggerAlerts();
}
