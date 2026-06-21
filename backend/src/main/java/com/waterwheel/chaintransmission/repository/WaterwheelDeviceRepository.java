package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WaterwheelDeviceRepository extends JpaRepository<WaterwheelDevice, Integer> {

    List<WaterwheelDevice> findByStatus(String status);

    WaterwheelDevice findByDeviceName(String deviceName);
}
