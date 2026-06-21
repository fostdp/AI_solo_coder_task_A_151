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
@Table(name = "efficiency_optimization")
public class EfficiencyOptimization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "optimization_id")
    private Long optimizationId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "optimization_time")
    private OffsetDateTime optimizationTime;

    @Column(name = "method", length = 50)
    private String method = "ResponseSurface";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scraper_shape_params", columnDefinition = "jsonb")
    private Map<String, Object> scraperShapeParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chain_speed_range", columnDefinition = "jsonb")
    private Map<String, Object> chainSpeedRange;

    @Column(name = "optimal_scraper_depth", precision = 10, scale = 4)
    private BigDecimal optimalScraperDepth;

    @Column(name = "optimal_scraper_width", precision = 10, scale = 4)
    private BigDecimal optimalScraperWidth;

    @Column(name = "optimal_scraper_angle", precision = 10, scale = 4)
    private BigDecimal optimalScraperAngle;

    @Column(name = "optimal_chain_speed", precision = 10, scale = 4)
    private BigDecimal optimalChainSpeed;

    @Column(name = "predicted_max_water_flow", precision = 12, scale = 4)
    private BigDecimal predictedMaxWaterFlow;

    @Column(name = "efficiency_improvement", precision = 8, scale = 4)
    private BigDecimal efficiencyImprovement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_surface_data", columnDefinition = "jsonb")
    private Map<String, Object> responseSurfaceData;

    @Column(name = "iterations")
    private Integer iterations;

    @Column(name = "convergence")
    private Boolean convergence = false;
}
