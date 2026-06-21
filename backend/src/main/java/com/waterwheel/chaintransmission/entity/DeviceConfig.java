package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "device_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"device_id", "param_name"})
})
public class DeviceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "param_name", nullable = false, length = 100)
    private String paramName;

    @Column(name = "param_value", precision = 14, scale = 6)
    private BigDecimal paramValue;

    @Column(name = "param_unit", length = 20)
    private String paramUnit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_time")
    private OffsetDateTime updatedTime;
}
