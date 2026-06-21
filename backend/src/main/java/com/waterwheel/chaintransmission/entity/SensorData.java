package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "sensor_data", indexes = {
    @Index(name = "idx_sensor_data_device_time", columnList = "device_id, time DESC"),
    @Index(name = "idx_sensor_data_time", columnList = "time DESC")
})
public class SensorData {

    @Id
    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "sprocket_speed", precision = 10, scale = 4)
    private BigDecimal sprocketSpeed;

    @Column(name = "sprocket_speed_unit", length = 10)
    private String sprocketSpeedUnit = "RPM";

    @Column(name = "scraper_load", precision = 12, scale = 4)
    private BigDecimal scraperLoad;

    @Column(name = "scraper_load_unit", length = 10)
    private String scraperLoadUnit = "N";

    @Column(name = "chain_tension", precision = 12, scale = 4)
    private BigDecimal chainTension;

    @Column(name = "chain_tension_unit", length = 10)
    private String chainTensionUnit = "N";

    @Column(name = "water_flow", precision = 12, scale = 4)
    private BigDecimal waterFlow;

    @Column(name = "water_flow_unit", length = 15)
    private String waterFlowUnit = "L/h";

    @Column(name = "vibration_amplitude", precision = 10, scale = 6)
    private BigDecimal vibrationAmplitude;

    @Column(name = "chain_elongation", precision = 10, scale = 6)
    private BigDecimal chainElongation;

    @Column(name = "torque", precision = 12, scale = 4)
    private BigDecimal torque;

    @Column(name = "torque_unit", length = 10)
    private String torqueUnit = "N·m";
}
