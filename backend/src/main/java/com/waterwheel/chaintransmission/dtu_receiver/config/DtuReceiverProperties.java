package com.waterwheel.chaintransmission.dtu_receiver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dtu.receiver")
public class DtuReceiverProperties {

    private boolean validationEnabled = true;
    private double minSprocketSpeed = 0.0;
    private double maxSprocketSpeed = 200.0;
    private double minChainTension = 0.0;
    private double maxChainTension = 100000.0;
    private double minWaterFlow = 0.0;
    private double maxWaterFlow = 100000.0;
    private double minScraperLoad = 0.0;
    private double maxScraperLoad = 50000.0;
    private boolean publishEvents = true;
}
