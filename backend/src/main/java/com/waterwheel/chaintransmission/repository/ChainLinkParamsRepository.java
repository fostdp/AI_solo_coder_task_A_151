package com.waterwheel.chaintransmission.repository;

import com.waterwheel.chaintransmission.entity.ChainLinkParams;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ChainLinkParamsRepository extends JpaRepository<ChainLinkParams, Long> {

    Optional<ChainLinkParams> findByDeviceId(Integer deviceId);
}
