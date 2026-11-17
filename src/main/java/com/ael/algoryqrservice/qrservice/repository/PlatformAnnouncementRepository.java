package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PlatformAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, Long> {

    List<PlatformAnnouncement> findAllByOrderByStartsAtDesc();

    List<PlatformAnnouncement> findByActiveTrueAndStartsAtLessThanEqualOrderByStartsAtDesc(LocalDateTime now);
}
