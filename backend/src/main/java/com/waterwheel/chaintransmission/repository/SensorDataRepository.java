package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, OffsetDateTime> {

    @Query("SELECT s FROM SensorData s WHERE s.deviceId = :deviceId ORDER BY s.time DESC LIMIT 1")
    Optional<SensorData> findLatestByDeviceId(@Param("deviceId") Integer deviceId);

    List<SensorData> findByDeviceIdAndTimeBetweenOrderByTimeAsc(
            Integer deviceId, OffsetDateTime startTime, OffsetDateTime endTime);

    @Query(value = "SELECT * FROM sensor_data WHERE device_id = :deviceId ORDER BY time DESC LIMIT :limit", nativeQuery = true)
    List<SensorData> findRecentByDeviceId(@Param("deviceId") Integer deviceId, @Param("limit") int limit);
}
