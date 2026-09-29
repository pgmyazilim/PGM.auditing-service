package org.pgmbim.audit.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pgmbim.audit.config.ClientCacheProps;
import org.pgmbim.audit.data.entity.Client;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.das.DataAccessServiceGrpc;
import org.pgmbim.grpc.das.DataResponse;
import org.pgmbim.grpc.das.ResponseStatus;
import org.pgmbim.grpc.das.SelectRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClientResolverTest {

    private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final DataResponse OK = DataResponse.newBuilder().setStatus(ResponseStatus.SUCCESS).build();

    private DataAccessServiceGrpc.DataAccessServiceBlockingStub das;
    private DasDataMapper mapper;
    private MutableClock clock;
    private ClientResolver resolver;

    @BeforeEach
    void setUp() {
        das = mock(DataAccessServiceGrpc.DataAccessServiceBlockingStub.class);
        mapper = mock(DasDataMapper.class);
        clock = new MutableClock(Instant.parse("2026-09-29T10:00:00Z"));
        resolver = new ClientResolver(das, mapper, new ClientCacheProps(Duration.ofMinutes(5)), clock);
    }

    @Test
    void blankKey_returnsNoneWithoutLookup() {
        ClientResolver.Resolution r = resolver.resolve("  ");

        assertNull(r.clientId());
        assertNull(r.warning());
        verifyNoInteractions(das);
    }

    @Test
    void malformedKey_returnsWarningWithoutLookup() {
        ClientResolver.Resolution r = resolver.resolve("not-a-guid");

        assertNull(r.clientId());
        assertNotNull(r.warning());
        assertTrue(r.warning().contains("Invalid client_id"));
        verifyNoInteractions(das);
    }

    @Test
    void knownKey_resolvesAndIsCached() {
        when(das.select(any(SelectRequest.class))).thenReturn(OK);
        when(mapper.mapFirstOptional(any(DataResponse.class), eq(Client.class)))
                .thenReturn(Optional.of(new Client(7, KEY, "Test")));

        ClientResolver.Resolution first = resolver.resolve(KEY.toUpperCase());
        ClientResolver.Resolution second = resolver.resolve(KEY);

        assertEquals(7, first.clientId());
        assertNull(first.warning());
        assertEquals(7, second.clientId());
        verify(das, times(1)).select(any(SelectRequest.class));
    }

    @Test
    void unknownKey_returnsWarningAndNegativeResultIsCached() {
        when(das.select(any(SelectRequest.class))).thenReturn(OK);
        when(mapper.mapFirstOptional(any(DataResponse.class), eq(Client.class))).thenReturn(Optional.empty());

        ClientResolver.Resolution first = resolver.resolve(KEY);
        ClientResolver.Resolution second = resolver.resolve(KEY);

        assertNull(first.clientId());
        assertTrue(first.warning().contains("Client not found"));
        assertNull(second.clientId());
        verify(das, times(1)).select(any(SelectRequest.class));
    }

    @Test
    void expiredEntry_isLookedUpAgain() {
        when(das.select(any(SelectRequest.class))).thenReturn(OK);
        when(mapper.mapFirstOptional(any(DataResponse.class), eq(Client.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new Client(9, KEY, "Yeni")));

        assertNull(resolver.resolve(KEY).clientId());
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertEquals(9, resolver.resolve(KEY).clientId());
        verify(das, times(2)).select(any(SelectRequest.class));
    }

    @Test
    void lookupFailure_returnsWarningAndIsNotCached() {
        when(das.select(any(SelectRequest.class)))
                .thenThrow(new RuntimeException("DAS down"))
                .thenReturn(OK);
        when(mapper.mapFirstOptional(any(DataResponse.class), eq(Client.class)))
                .thenReturn(Optional.of(new Client(7, KEY, "Test")));

        ClientResolver.Resolution failed = resolver.resolve(KEY);
        ClientResolver.Resolution retried = resolver.resolve(KEY);

        assertNull(failed.clientId());
        assertTrue(failed.warning().contains("Client lookup failed"));
        assertEquals(7, retried.clientId());
        verify(das, times(2)).select(any(SelectRequest.class));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
