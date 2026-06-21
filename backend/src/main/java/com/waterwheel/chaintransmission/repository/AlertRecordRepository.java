package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    List<AlertRecord> findByDeviceIdOrderByAlertTimeDesc(Integer deviceId);

    List<AlertRecord> findByAlertTimeBetweenOrderByAlertTimeDesc(
            OffsetDateTime startTime, OffsetDateTime endTime);

    List<AlertRecord> findByAlertLevelAndAlertTimeAfterOrderByAlertTimeDesc(
            String alertLevel, OffsetDateTime afterTime);

    @Query("SELECT a FROM AlertRecord a WHERE a.deviceId = :deviceId AND a.alertTime > :after ORDER BY a.alertTime DESC")
    List<AlertRecord> findRecentByDeviceId(@Param("deviceId") Integer deviceId, @Param("after") OffsetDateTime after);

    @Query(value = "SELECT * FROM alert_record WHERE acknowledged = false ORDER BY alert_time DESC LIMIT :limit", nativeQuery = true)
    List<AlertRecord> findUnacknowledged(@Param("limit") int limit);
}
