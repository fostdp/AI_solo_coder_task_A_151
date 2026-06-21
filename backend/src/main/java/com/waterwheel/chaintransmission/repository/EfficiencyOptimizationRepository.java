package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.EfficiencyOptimization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EfficiencyOptimizationRepository extends JpaRepository<EfficiencyOptimization, Long> {

    List<EfficiencyOptimization> findByDeviceIdOrderByOptimizationTimeDesc(Integer deviceId);

    EfficiencyOptimization findTopByDeviceIdOrderByOptimizationTimeDesc(Integer deviceId);
}
