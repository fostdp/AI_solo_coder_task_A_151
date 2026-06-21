package com.waterwheel.chaintransmission.controller;

import com.waterwheel.chaintransmission.dto.*;
import com.waterwheel.chaintransmission.entity.*;
import com.waterwheel.chaintransmission.service.WaterwheelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class WaterwheelController {

    @Autowired
    private WaterwheelService waterwheelService;

    @Autowired
    private com.waterwheel.chaintransmission.dtu_receiver.service.DtuReceiverService dtuReceiverService;

    @GetMapping("/devices")
    public ResponseEntity<List<WaterwheelDevice>> getAllDevices() {
        return ResponseEntity.ok(waterwheelService.getAllDevices());
    }

    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<WaterwheelDevice> getDeviceById(@PathVariable Integer deviceId) {
        return waterwheelService.getDeviceById(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/devices")
    public ResponseEntity<WaterwheelDevice> createDevice(@RequestBody WaterwheelDevice device) {
        WaterwheelDevice saved = waterwheelService.saveDevice(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/sensor-data")
    public ResponseEntity<SensorData> receiveSensorData(@RequestBody SensorDataDTO dto) {
        log.debug("接收传感器数据: deviceId={}, sprocketSpeed={}", dto.getDeviceId(), dto.getSprocketSpeed());
        SensorData saved = waterwheelService.saveSensorData(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/sensor-data/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchSensorData(@RequestBody List<SensorDataDTO> dtos) {
        Map<String, Integer> counts = dtuReceiverService.receiveBatch(dtos);
        Map<String, Object> result = new HashMap<>(counts);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sensor-data/{deviceId}/latest")
    public ResponseEntity<SensorData> getLatestSensorData(@PathVariable Integer deviceId) {
        return waterwheelService.getLatestSensorData(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sensor-data/{deviceId}")
    public ResponseEntity<List<SensorData>> getSensorDataByDevice(
            @PathVariable Integer deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return ResponseEntity.ok(waterwheelService.getSensorDataByDevice(deviceId, startTime, endTime));
    }

    @GetMapping("/sensor-data/{deviceId}/recent")
    public ResponseEntity<List<SensorData>> getRecentSensorData(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(waterwheelService.getRecentSensorData(deviceId, limit));
    }

    @GetMapping("/sensor-data/{deviceId}/statistics")
    public ResponseEntity<Map<String, Object>> getSensorDataStatistics(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(waterwheelService.getSensorDataStatistics(deviceId, hours));
    }

    @PostMapping("/simulation/chain-dynamics/{deviceId}")
    public ResponseEntity<ChainDynamicsResultDTO> runChainDynamicsSimulation(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "15.0") double inputSpeedRPM,
            @RequestParam(defaultValue = "50.0") double inputTorque) {
        log.info("启动链传动动力学仿真: deviceId={}, speed={} RPM, torque={} N·m",
                deviceId, inputSpeedRPM, inputTorque);
        ChainDynamicsResultDTO result = waterwheelService.runChainDynamicsSimulation(
                deviceId, inputSpeedRPM, inputTorque);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/simulation/chain-dynamics/{deviceId}/history")
    public ResponseEntity<List<ChainDynamicsSimulation>> getSimulationHistory(@PathVariable Integer deviceId) {
        return ResponseEntity.ok(waterwheelService.getSimulationHistory(deviceId));
    }

    @PostMapping("/optimization/efficiency/{deviceId}")
    public ResponseEntity<OptimizationResultDTO> runEfficiencyOptimization(@PathVariable Integer deviceId) {
        log.info("启动提水效率优化: deviceId={}", deviceId);
        OptimizationResultDTO result = waterwheelService.runEfficiencyOptimization(deviceId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/optimization/efficiency/{deviceId}/history")
    public ResponseEntity<List<EfficiencyOptimization>> getOptimizationHistory(@PathVariable Integer deviceId) {
        return ResponseEntity.ok(waterwheelService.getOptimizationHistory(deviceId));
    }

    @PostMapping("/alerts/{deviceId}")
    public ResponseEntity<AlertDTO> triggerAlert(
            @PathVariable Integer deviceId,
            @RequestParam String alertType,
            @RequestParam(defaultValue = "WARNING") String alertLevel,
            @RequestParam String message) {
        AlertDTO alert = waterwheelService.triggerAlert(deviceId, alertType, alertLevel, message, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    @GetMapping("/alerts/device/{deviceId}")
    public ResponseEntity<List<AlertDTO>> getAlertsByDevice(@PathVariable Integer deviceId) {
        return ResponseEntity.ok(waterwheelService.getAlertsByDevice(deviceId));
    }

    @GetMapping("/alerts/recent")
    public ResponseEntity<List<AlertDTO>> getRecentAlerts(@RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(waterwheelService.getRecentAlerts(hours));
    }

    @GetMapping("/alerts/unacknowledged")
    public ResponseEntity<List<AlertDTO>> getUnacknowledgedAlerts(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(waterwheelService.getUnacknowledgedAlerts(limit));
    }

    @PutMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<AlertDTO> acknowledgeAlert(@PathVariable Long alertId) {
        return ResponseEntity.ok(waterwheelService.acknowledgeAlert(alertId));
    }

    @GetMapping("/configs/{deviceId}")
    public ResponseEntity<List<DeviceConfig>> getDeviceConfigs(@PathVariable Integer deviceId) {
        return ResponseEntity.ok(waterwheelService.getDeviceConfigs(deviceId));
    }

    @PutMapping("/configs")
    public ResponseEntity<DeviceConfig> updateDeviceConfig(@RequestBody DeviceConfig config) {
        return ResponseEntity.ok(waterwheelService.updateDeviceConfig(config));
    }

    @GetMapping("/chain-params/{deviceId}")
    public ResponseEntity<ChainLinkParams> getChainLinkParams(@PathVariable Integer deviceId) {
        return waterwheelService.getChainLinkParams(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/chain-params")
    public ResponseEntity<ChainLinkParams> saveChainLinkParams(@RequestBody ChainLinkParams params) {
        return ResponseEntity.ok(waterwheelService.saveChainLinkParams(params));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Ancient Waterwheel Chain Transmission Simulation System");
        health.put("version", "1.0.0");
        health.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(health);
    }
}
