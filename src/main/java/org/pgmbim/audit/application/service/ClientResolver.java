package org.pgmbim.audit.application.service;

import org.pgmbim.audit.config.ClientCacheProps;
import org.pgmbim.audit.data.entity.Client;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.das.DataAccessServiceGrpc;
import org.pgmbim.grpc.das.DataResponse;
import org.pgmbim.grpc.das.SelectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.*;

/**
 * İstemcinin gönderdiği ClientKey'i (GUID) aaa.Clients.ClientId'ye çözer. Sonuçlar (bulunamayanlar dahil)
 * TTL süresince bellekte tutulur; DAS hatası cache'lenmez. Çözülemeyen her durumda audit yine yazılır,
 * yalnızca ClientId NULL kalır ve çağırana bir uyarı döner.
 */
@Component
public class ClientResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientResolver.class);
    private static final String CLIENTS_TABLE = "Clients";
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_ECHOED_LENGTH = 64;

    private final DataAccessServiceGrpc.DataAccessServiceBlockingStub dasClient;
    private final DasDataMapper dataMapper;
    private final Duration ttl;
    private final Clock clock;
    private final int maxEntries;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public ClientResolver(DataAccessServiceGrpc.DataAccessServiceBlockingStub dasClient,
                          DasDataMapper dataMapper,
                          ClientCacheProps props) {
        this(dasClient, dataMapper, props, Clock.systemUTC(), MAX_ENTRIES);
    }

    ClientResolver(DataAccessServiceGrpc.DataAccessServiceBlockingStub dasClient,
                   DasDataMapper dataMapper,
                   ClientCacheProps props,
                   Clock clock) {
        this(dasClient, dataMapper, props, clock, MAX_ENTRIES);
    }

    ClientResolver(DataAccessServiceGrpc.DataAccessServiceBlockingStub dasClient,
                   DasDataMapper dataMapper,
                   ClientCacheProps props,
                   Clock clock,
                   int maxEntries) {
        this.dasClient = dasClient;
        this.dataMapper = dataMapper;
        this.ttl = props.ttl();
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    public Resolution resolve(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return Resolution.NONE;
        }
        String trimmed = clientKey.trim();
        if (!GUID.matcher(trimmed).matches()) {
            return Resolution.unresolved("Invalid client_id format. client_id=" + truncate(trimmed), true);
        }

        String key = trimmed.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return toResolution(key, cached.clientId(), false);
        }

        Optional<Integer> found;
        try {
            found = lookup(key);
        } catch (RuntimeException ex) {
            log.debug("Client lookup failed. clientKey={}", key, ex);
            return Resolution.unresolved("Client lookup failed. client_id=" + truncate(key), true);
        }

        Integer clientId = found.orElse(null);
        storeInCache(key, clientId, now);
        return toResolution(key, clientId, true);
    }

    private void storeInCache(String key, Integer clientId, Instant now) {
        cache.put(key, new CacheEntry(clientId, now.plus(ttl)));
        if (cache.size() > maxEntries) {
            cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
            if (cache.size() > maxEntries) {
                cache.clear();
            }
        }
    }

    private Optional<Integer> lookup(String key) {
        SelectRequest request = select(CLIENTS_TABLE)
                .setFilterGroup(and(eq("ClientKey", key)))
                .setPagination(page(1, 0))
                .build();

        DataResponse response = dasClient.select(request);
        ok(response);
        return dataMapper.mapFirstOptional(response, Client.class).map(Client::id);
    }

    private Resolution toResolution(String key, Integer clientId, boolean fresh) {
        if (clientId == null) {
            return Resolution.unresolved("Client not found. client_id=" + truncate(key), fresh);
        }
        return Resolution.resolved(clientId, fresh);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_ECHOED_LENGTH ? value : value.substring(0, MAX_ECHOED_LENGTH);
    }

    public record Resolution(Integer clientId, String warning, boolean fresh) {
        static final Resolution NONE = new Resolution(null, null, true);

        static Resolution unresolved(String warning, boolean fresh) {
            return new Resolution(null, warning, fresh);
        }

        static Resolution resolved(Integer clientId, boolean fresh) {
            return new Resolution(clientId, null, fresh);
        }
    }

    private record CacheEntry(Integer clientId, Instant expiresAt) {
    }
}
