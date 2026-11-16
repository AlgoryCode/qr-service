package com.ael.algoryqrservice.shared.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Schema of {@code scheduler_lock}; {@link SchedulerLock} reads and writes it with plain SQL. */
@Entity
@Table(name = "scheduler_lock")
@Getter
@Setter
public class SchedulerLockEntity {

    @Id
    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;
}
