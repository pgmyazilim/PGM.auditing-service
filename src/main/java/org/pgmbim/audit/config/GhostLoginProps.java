package org.pgmbim.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gizli süper kullanıcı ("ghost") için audit baskılama. enabled+userId eşleşirse hiçbir kayıt yazılmaz.
 */
@ConfigurationProperties(prefix = "ghost-login")
public record GhostLoginProps(boolean enabled, long userId) {

    public boolean isGhostUser(long candidateUserId) {
        return enabled && candidateUserId == userId;
    }
}
