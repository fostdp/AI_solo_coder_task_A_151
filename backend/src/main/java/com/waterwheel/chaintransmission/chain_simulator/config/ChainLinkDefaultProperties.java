package com.waterwheel.chaintransmission.chain_simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "simulation.chain.default")
public class ChainLinkDefaultProperties {

    private double linkMass = 0.25;
    private double linkLength = 0.125;
    private double stiffness = 500000.0;
    private double damping = 150.0;
    private double friction = 0.15;
    private double allowableTension = 15000.0;
}
