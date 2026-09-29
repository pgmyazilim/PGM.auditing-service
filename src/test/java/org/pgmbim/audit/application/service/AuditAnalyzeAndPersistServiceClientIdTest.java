package org.pgmbim.audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pgmbim.audit.application.model.AuditPersistenceResult;
import org.pgmbim.audit.config.GhostLoginProps;
import org.pgmbim.audit.data.entity.Action;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.aaa.audit.AnalyzeAndPersistRequest;
import org.pgmbim.grpc.aaa.audit.OperationType;
import org.pgmbim.grpc.aaa.audit.Outcome;
import org.pgmbim.grpc.das.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditAnalyzeAndPersistServiceClientIdTest {

    private static final String KEY = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    private DataAccessServiceGrpc.DataAccessServiceBlockingStub das;
    private ClientResolver clientResolver;
    private AuditAnalyzeAndPersistService service;

    @BeforeEach
    void setUp() {
        das = mock(DataAccessServiceGrpc.DataAccessServiceBlockingStub.class);
        DasDataMapper mapper = mock(DasDataMapper.class);
        clientResolver = mock(ClientResolver.class);

        DataResponse ok = DataResponse.newBuilder().setStatus(ResponseStatus.SUCCESS).build();
        when(das.select(any(SelectRequest.class))).thenReturn(ok);
        when(mapper.mapFirstOptional(any(DataResponse.class), eq(Action.class))).thenReturn(Optional.of(
                new Action(11, null, 3, "Test", null, "TEST_ACTION", true, true, true, true)));
        when(das.insert(any(InsertRequest.class))).thenReturn(DataResponse.newBuilder()
                .setStatus(ResponseStatus.SUCCESS)
                .putGeneratedKeys("ActionLogId", TypedValue.newBuilder().setInt64Value(55L).build())
                .build());

        service = new AuditAnalyzeAndPersistService(das, mapper, new ObjectMapper(),
                new GhostLoginProps(false, 0), clientResolver);
    }

    private AnalyzeAndPersistRequest request(String clientId) {
        return AnalyzeAndPersistRequest.newBuilder()
                .setActionKey("TEST_ACTION")
                .setOperationType(OperationType.SELECT)
                .setOutcome(Outcome.SUCCESS)
                .setUserId(1)
                .setClientId(clientId)
                .build();
    }

    private InsertRequest capturedInsert() {
        ArgumentCaptor<InsertRequest> captor = ArgumentCaptor.forClass(InsertRequest.class);
        verify(das).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void resolvedClient_isWrittenToActionLog() {
        when(clientResolver.resolve(KEY)).thenReturn(new ClientResolver.Resolution(7, null, true));

        AuditPersistenceResult result = service.analyzeAndPersist(request(KEY));

        TypedValue clientId = capturedInsert().getData().getFieldsMap().get("ClientId");
        assertNotNull(clientId);
        assertEquals(7, clientId.getInt32Value());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void unresolvedClient_logIsStillWrittenWithoutClientIdAndWarns() {
        when(clientResolver.resolve(KEY))
                .thenReturn(new ClientResolver.Resolution(null, "Client not found. client_id=" + KEY, true));

        AuditPersistenceResult result = service.analyzeAndPersist(request(KEY));

        assertFalse(capturedInsert().getData().getFieldsMap().containsKey("ClientId"));
        assertTrue(result.operationHistoryWritten());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Client not found")));
    }

    @Test
    void emptyClientId_logIsWrittenWithoutClientIdAndNoWarning() {
        when(clientResolver.resolve("")).thenReturn(new ClientResolver.Resolution(null, null, true));

        AuditPersistenceResult result = service.analyzeAndPersist(request(""));

        assertFalse(capturedInsert().getData().getFieldsMap().containsKey("ClientId"));
        assertTrue(result.warnings().isEmpty());
    }
}
