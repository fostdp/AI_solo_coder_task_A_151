package com.waterwheel.chaintransmission.chain_simulator.service;

import com.waterwheel.chaintransmission.chain_simulator.config.ChainDynamicsProperties;
import com.waterwheel.chaintransmission.chain_simulator.config.ChainLinkDefaultProperties;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.entity.ChainDynamicsSimulation;
import com.waterwheel.chaintransmission.entity.ChainLinkParams;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.events.ChainSimulationCompletedEvent;
import com.waterwheel.chaintransmission.events.SensorDataReceivedEvent;
import com.waterwheel.chaintransmission.repository.ChainDynamicsSimulationRepository;
import com.waterwheel.chaintransmission.repository.ChainLinkParamsRepository;
import com.waterwheel.chaintransmission.simulation.ChainDynamicsSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ChainSimulatorService {

    @Autowired
    private ChainDynamicsSimulator chainDynamicsSimulator;

    @Autowired
    private ChainDynamicsSimulationRepository simulationRepository;

    @Autowired
    private ChainLinkParamsRepository chainLinkParamsRepository;

    @Autowired
    private ChainLinkDefaultProperties defaultChainProps;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final Map<Integer, Long> lastSimulationTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MIN_SIMULATION_INTERVAL_MS = 10_000;

    public ChainDynamicsResultDTO runSimulation(WaterwheelDevice device, double inputSpeedRPM, double inputTorque) {
        ChainLinkParams params = chainLinkParamsRepository.findByDeviceId(device.getDeviceId())
                .orElseGet(() -> getDefaultParams(device.getDeviceId()));

        ChainDynamicsResultDTO result = chainDynamicsSimulator.simulate(device, params, inputSpeedRPM, inputTorque);

        persistResult(device.getDeviceId(), result, inputSpeedRPM, inputTorque);

        eventPublisher.publishEvent(new ChainSimulationCompletedEvent(this, device.getDeviceId(), result));
        log.info("发布 ChainSimulationCompletedEvent, deviceId={}, 耗时={}ms",
                device.getDeviceId(), result.getSimulationDurationMs());
        return result;
    }

    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        Long lastRun = lastSimulationTime.get(event.getDeviceId());
        long now = System.currentTimeMillis();
        if (lastRun != null && (now - lastRun) < MIN_SIMULATION_INTERVAL_MS) {
            return;
        }
        lastSimulationTime.put(event.getDeviceId(), now);
        log.debug("收到传感器数据事件, deviceId={}, 但自动模拟触发暂未启用（需人工调用）", event.getDeviceId());
    }

    private void persistResult(Integer deviceId, ChainDynamicsResultDTO result, double inputSpeedRPM, double inputTorque) {
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
    }

    private ChainLinkParams getDefaultParams(Integer deviceId) {
        ChainLinkParams params = new ChainLinkParams();
        params.setDeviceId(deviceId);
        params.setLinkMass(BigDecimal.valueOf(defaultChainProps.getLinkMass()));
        params.setLinkLength(BigDecimal.valueOf(defaultChainProps.getLinkLength()));
        params.setLinkStiffness(BigDecimal.valueOf(defaultChainProps.getStiffness()));
        params.setLinkDamping(BigDecimal.valueOf(defaultChainProps.getDamping()));
        params.setFrictionCoefficient(BigDecimal.valueOf(defaultChainProps.getFriction()));
        params.setAllowableTension(BigDecimal.valueOf(defaultChainProps.getAllowableTension()));
        params.setMaterial("锻铁");
        return chainLinkParamsRepository.save(params);
    }
}
