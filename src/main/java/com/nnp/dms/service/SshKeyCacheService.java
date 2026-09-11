/**
 * SshKeyCacheService.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// In-memory cache for SSH private keys, keyed by deployment ID.
// Because keys are never persisted, this is the single source of truth for credentials
// used by every later task (health check, container ops, terminal, etc.).
@Service
@Slf4j
public class SshKeyCacheService {

    @Value("${dms.ssh-key.ttl-minutes:60}")
    private long ttlMinutes = 60;

    // Concurrent map so parallel scheduler/async tasks can call it safely.
    private final Map<String, CachedKey> cache = new ConcurrentHashMap<>();

    // Cache (or re-cache) a key with an expiry = now + TTL.
    public void put(String deploymentId, String privateKey, String passphrase) {
        cache.put(deploymentId, new CachedKey(privateKey, passphrase, LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(ttlMinutes)));
        log.info("SSH key cached for deployment {} (TTL {} minutes)", deploymentId, ttlMinutes);
    }

    // Return the cached key if present and not expired; expired entries are evicted.
    public Optional<CachedKey> get(String deploymentId) {
        CachedKey entry = cache.get(deploymentId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt.isBefore(LocalDateTime.now(ZoneId.systemDefault()))) {
            cache.remove(deploymentId);
            log.warn("SSH key for deployment {} expired, removed from cache", deploymentId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void remove(String deploymentId) {
        cache.remove(deploymentId);
    }

    // Sweep job (configurable via dms.ssh-key.purge-delay-ms, default every 60s) that removes any already-expired keys in the background.
    @Scheduled(fixedDelayString = "${dms.ssh-key.purge-delay-ms:60000}")
    public void purgeExpired() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        cache.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    // Value object: the key material plus the timestamp after which it must not be used.
    public record CachedKey(String privateKey, String passphrase, LocalDateTime expiresAt) {
        public CachedKey {
            expiresAt = expiresAt == null ? LocalDateTime.now(ZoneId.systemDefault()) : expiresAt;
        }
    }
}
