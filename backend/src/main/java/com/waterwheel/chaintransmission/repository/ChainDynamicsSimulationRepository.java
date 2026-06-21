package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.ChainDynamicsSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChainDynamicsSimulationRepository extends JpaRepository<ChainDynamicsSimulation, Long> {

    List<ChainDynamicsSimulation> findByDeviceIdOrderBySimulationTimeDesc(Integer deviceId);

    ChainDynamicsSimulation findTopByDeviceIdOrderBySimulationTimeDesc(Integer deviceId);
}
