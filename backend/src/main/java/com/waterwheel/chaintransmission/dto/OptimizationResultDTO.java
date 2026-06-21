package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class OptimizationResultDTO {

    private Integer deviceId;
    private String method;
    private BigDecimal optimalScraperDepth;
    private BigDecimal optimalScraperWidth;
    private BigDecimal optimalScraperAngle;
    private BigDecimal optimalChainSpeed;
    private BigDecimal predictedMaxWaterFlow;
    private BigDecimal efficiencyImprovement;
    private Integer iterations;
    private Boolean convergence;
    private List<Map<String, Object>> designPoints;
    private Map<String, Object> responseSurfaceEquation;
}
