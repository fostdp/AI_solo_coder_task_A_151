package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "waterwheel_device")
public class WaterwheelDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Integer deviceId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "chain_length", precision = 10, scale = 2)
    private BigDecimal chainLength;

    @Column(name = "num_links")
    private Integer numLinks;

    @Column(name = "sprocket_radius", precision = 10, scale = 2)
    private BigDecimal sprocketRadius;

    @Column(name = "scraper_count")
    private Integer scraperCount;

    @Column(name = "scraper_shape", length = 50)
    private String scraperShape;

    @Column(name = "installation_date")
    private LocalDateTime installationDate;

    @Column(name = "status", length = 20)
    private String status;
}
