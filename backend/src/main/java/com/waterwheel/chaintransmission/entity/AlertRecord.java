package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "alert_record", indexes = {
    @Index(name = "idx_alert_device_time", columnList = "device_id, alert_time DESC"),
    @Index(name = "idx_alert_level", columnList = "alert_level")
})
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Column(name = "alert_time", nullable = false)
    private OffsetDateTime alertTime;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "alert_level", nullable = false, length = 20)
    private String alertLevel;

    @Column(name = "alert_message", columnDefinition = "TEXT")
    private String alertMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensor_data", columnDefinition = "jsonb")
    private Map<String, Object> sensorData;

    @Column(name = "mqtt_topic", length = 100)
    private String mqttTopic;

    @Column(name = "acknowledged")
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_time")
    private OffsetDateTime acknowledgedTime;
}
