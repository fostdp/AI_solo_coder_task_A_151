package com.waterwheel.chaintransmission.efficiency_optimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "optimization.chain-speed")
public class ChainSpeedOptimizationProperties {

    private double min = 0.5;
    private double max = 3.0;
}
