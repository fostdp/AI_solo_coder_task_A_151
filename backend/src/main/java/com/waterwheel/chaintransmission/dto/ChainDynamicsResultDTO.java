package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ChainDynamicsResultDTO {

    private Integer deviceId;
    private BigDecimal inputSpeed;
    private BigDecimal inputTorque;
    private Integer linkCount;
    private List<Double> tensionDistribution;
    private List<Double> vibrationFrequencies;
    private List<Double> collisionForces;
    private BigDecimal maxTension;
    private BigDecimal minTension;
    private BigDecimal avgTension;
    private Boolean resonanceRisk;
    private Integer simulationDurationMs;
    private Map<String, Object> linkPositions;
    private Map<String, Object> linkVelocities;
}
