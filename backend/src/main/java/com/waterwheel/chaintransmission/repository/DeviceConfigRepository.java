package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {

    List<DeviceConfig> findByDeviceId(Integer deviceId);

    Optional<DeviceConfig> findByDeviceIdAndParamName(Integer deviceId, String paramName);
}
