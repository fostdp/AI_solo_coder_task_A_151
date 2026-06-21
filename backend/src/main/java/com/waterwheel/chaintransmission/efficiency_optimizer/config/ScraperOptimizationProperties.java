package com.waterwheel.chaintransmission.efficiency_optimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "optimization.scraper")
public class ScraperOptimizationProperties {

    private double minDepth = 0.05;
    private double maxDepth = 0.20;
    private double minWidth = 0.10;
    private double maxWidth = 0.40;
    private double minAngle = 15.0;
    private double maxAngle = 60.0;
}
