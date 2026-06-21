package com.waterwheel.chaintransmission.efficiency_optimizer.service;

import com.waterwheel.chaintransmission.dto.OptimizationResultDTO;
import com.waterwheel.chaintransmission.efficiency_optimizer.config.ChainSpeedOptimizationProperties;
import com.waterwheel.chaintransmission.efficiency_optimizer.config.ResponseSurfaceProperties;
import com.waterwheel.chaintransmission.efficiency_optimizer.config.ScraperOptimizationProperties;
import com.waterwheel.chaintransmission.entity.EfficiencyOptimization;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.events.EfficiencyOptimizationCompletedEvent;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.EfficiencyOptimizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
public class EfficiencyOptimizerService {

    @Autowired
    private WaterEfficiencyOptimizer waterEfficiencyOptimizer;

    @Autowired
    private EfficiencyOptimizationRepository optimizationRepository;

    @Autowired
    private ScraperOptimizationProperties scraperProps;

    @Autowired
    private ChainSpeedOptimizationProperties chainSpeedProps;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public OptimizationResultDTO runOptimization(WaterwheelDevice device, double currentWaterFlow) {
        OptimizationResultDTO result = waterEfficiencyOptimizer.optimize(device, currentWaterFlow);

        persistResult(device.getDeviceId(), result);

        eventPublisher.publishEvent(new EfficiencyOptimizationCompletedEvent(this, device.getDeviceId(), result));
        log.info("发布 EfficiencyOptimizationCompletedEvent, deviceId={}, 效率提升={}%",
                device.getDeviceId(), result.getEfficiencyImprovement());
        return result;
    }

    private void persistResult(Integer deviceId, OptimizationResultDTO result) {
        EfficiencyOptimization entity = new EfficiencyOptimization();
        entity.setDeviceId(deviceId);
        entity.setOptimizationTime(OffsetDateTime.now());
        entity.setMethod(result.getMethod());
        entity.setScraperShapeParams(Map.of(
                "minDepth", scraperProps.getMinDepth(), "maxDepth", scraperProps.getMaxDepth(),
                "minWidth", scraperProps.getMinWidth(), "maxWidth", scraperProps.getMaxWidth(),
                "minAngle", scraperProps.getMinAngle(), "maxAngle", scraperProps.getMaxAngle()
        ));
        entity.setChainSpeedRange(Map.of("min", chainSpeedProps.getMin(), "max", chainSpeedProps.getMax()));
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
    }
}
