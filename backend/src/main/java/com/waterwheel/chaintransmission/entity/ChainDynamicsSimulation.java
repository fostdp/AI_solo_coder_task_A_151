package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "chain_dynamics_simulation")
public class ChainDynamicsSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "simulation_id")
    private Long simulationId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "simulation_time")
    private OffsetDateTime simulationTime;

    @Column(name = "input_speed", precision = 10, scale = 4)
    private BigDecimal inputSpeed;

    @Column(name = "input_torque", precision = 12, scale = 4)
    private BigDecimal inputTorque;

    @Column(name = "link_count")
    private Integer linkCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tension_distribution", columnDefinition = "jsonb")
    private Map<String, Object> tensionDistribution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vibration_frequencies", columnDefinition = "jsonb")
    private Map<String, Object> vibrationFrequencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collision_forces", columnDefinition = "jsonb")
    private Map<String, Object> collisionForces;

    @Column(name = "max_tension", precision = 12, scale = 4)
    private BigDecimal maxTension;

    @Column(name = "min_tension", precision = 12, scale = 4)
    private BigDecimal minTension;

    @Column(name = "avg_tension", precision = 12, scale = 4)
    private BigDecimal avgTension;

    @Column(name = "resonance_risk")
    private Boolean resonanceRisk = false;

    @Column(name = "simulation_duration_ms")
    private Integer simulationDurationMs;
}
