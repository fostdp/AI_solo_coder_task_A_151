package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class SensorDataDTO {

    private OffsetDateTime time;
    private Integer deviceId;
    private BigDecimal sprocketSpeed;
    private String sprocketSpeedUnit;
    private BigDecimal scraperLoad;
    private String scraperLoadUnit;
    private BigDecimal chainTension;
    private String chainTensionUnit;
    private BigDecimal waterFlow;
    private String waterFlowUnit;
    private BigDecimal vibrationAmplitude;
    private BigDecimal chainElongation;
    private BigDecimal torque;
    private String torqueUnit;
}
