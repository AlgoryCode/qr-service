package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceSessionItemRepository extends JpaRepository<DeviceSessionItem, Long> {

    List<DeviceSessionItem> findBySessionIdAndDeletedFalseOrderByIdAsc(Long sessionId);

    List<DeviceSessionItem> findBySessionIdInAndDeletedFalseOrderByIdAsc(Collection<Long> sessionIds);

    Optional<DeviceSessionItem> findByIdAndSessionIdAndDeletedFalse(Long id, Long sessionId);
}
