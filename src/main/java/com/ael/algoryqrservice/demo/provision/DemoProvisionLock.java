package com.ael.algoryqrservice.demo.provision;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/** Ayni userId icin eszamanli demo provision cagrilarini (register + refresh) serilestirir. */
@Component
public class DemoProvisionLock {

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public void runExclusive(Long userId, Runnable action) {
        if (userId == null) {
            action.run();
            return;
        }
        Object lock = locks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (lock) {
            try {
                action.run();
            } finally {
                locks.remove(userId, lock);
            }
        }
    }
}
