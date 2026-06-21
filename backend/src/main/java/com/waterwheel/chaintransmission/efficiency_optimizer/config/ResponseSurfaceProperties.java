package com.waterwheel.chaintransmission.efficiency_optimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "optimization.response-surface")
public class ResponseSurfaceProperties {

    private int designPoints = 15;
    private int maxIterations = 50;
    private double convergenceTolerance = 1.0e-6;
    private double adaptiveResidualThreshold = 0.05;
    private double boundaryWeightBoost = 2.5;
    private int adaptiveRefinementDirections = 12;
}
