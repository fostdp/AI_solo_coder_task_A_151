package com.waterwheel.chaintransmission.chain_simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "simulation.dynamics")
public class ChainDynamicsProperties {

    private double timeStep = 0.001;
    private double simulationDuration = 2.0;
    private int maxIterations = 2000;
    private double gridCellMultiplier = 2.5;
    private int maxAdaptiveSubsteps = 4;
    private double highSpeedThreshold = 3.0;
    private int collisionCheckStrideLowSpeed = 2;
    private double collisionRestitution = 0.3;
    private double gravity = 9.81;
}
