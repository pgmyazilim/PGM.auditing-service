package org.pgmbim.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * ClientKey (GUID) → ClientId çözüm cache'inin yaşam süresi. Bulunamayan anahtarlar da bu süre boyunca
 * cache'lenir; yeni eklenen bir istemci en geç TTL sonunda tanınır.
 */
@ConfigurationProperties(prefix = "client-cache")
public record ClientCacheProps(Duration ttl) {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public ClientCacheProps {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = DEFAULT_TTL;
        }
    }
}
