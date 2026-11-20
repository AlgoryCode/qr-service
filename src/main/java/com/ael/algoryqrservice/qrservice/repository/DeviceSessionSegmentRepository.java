package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DeviceSessionSegmentRepository extends JpaRepository<DeviceSessionSegment, Long> {

    List<DeviceSessionSegment> findBySessionIdAndDeletedFalseOrderByStartedAtAscIdAsc(Long sessionId);

    List<DeviceSessionSegment> findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(Collection<Long> sessionIds);
}
