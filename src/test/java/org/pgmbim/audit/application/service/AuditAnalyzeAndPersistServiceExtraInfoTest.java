package org.pgmbim.audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pgmbim.audit.config.GhostLoginProps;
import org.pgmbim.audit.data.entity.Action;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.aaa.audit.AnalyzeAndPersistRequest;
import org.pgmbim.grpc.aaa.audit.ErrorDetail;
import org.pgmbim.grpc.aaa.audit.KeyValue;
import org.pgmbim.grpc.aaa.audit.OperationType;
import org.pgmbim.grpc.aaa.audit.Outcome;
import org.pgmbim.grpc.das.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditAnalyzeAndPersistServiceExtraInfoTest {

    private static final String IP = "10.20.30.40";
    private static final String ERROR = "invalid_credentials";

    private DataAccessServiceGrpc.DataAccessServiceBlockingStub das;
    private AuditAnalyzeAndPersistService service;

    @BeforeEach
    void setUp() {
        das = mock(DataAccessServiceGrpc.DataAccessServiceBlockingStub.class);
        DasDataMapper mapper = mock(DasDataMapper.class);
        ClientResolver clientResolver = mock(ClientResolver.class);
        when(clientResolver.resolve(any())).thenReturn(new ClientResolver.Resolution(null, null, true));

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

    private AnalyzeAndPersistRequest.Builder request(Outcome outcome) {
        AnalyzeAndPersistRequest.Builder builder = AnalyzeAndPersistRequest.newBuilder()
                .setActionKey("TEST_ACTION")
                .setOperationType(OperationType.SELECT)
                .setOutcome(outcome)
                .setUserId(1);
        if (outcome == Outcome.FAILURE) {
            builder.setError(ErrorDetail.newBuilder().setPublicMessage(ERROR).build());
        }
        return builder;
    }

    private static KeyValue kv(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(value).build();
    }

    private InsertRequest capturedInsert() {
        ArgumentCaptor<InsertRequest> captor = ArgumentCaptor.forClass(InsertRequest.class);
        verify(das).insert(captor.capture());
        return captor.getValue();
    }

    private String capturedExtraInfo() {
        TypedValue extraInfo = capturedInsert().getData().getFieldsMap().get("ExtraInfo");
        assertNotNull(extraInfo);
        return extraInfo.getStringValue();
    }

    @Test
    void failureWithIp_keepsErrorMessageAndAddsIp() {
        service.analyzeAndPersist(request(Outcome.FAILURE).addExtraInfo(kv("ip", IP)).build());

        assertEquals("{\"err_msg\":\"" + ERROR + "\",\"ip\":\"" + IP + "\"}", capturedExtraInfo());
    }

    @Test
    void failureWithoutExtraInfo_isUnchanged() {
        service.analyzeAndPersist(request(Outcome.FAILURE).build());

        assertEquals("{\"err_msg\":\"" + ERROR + "\"}", capturedExtraInfo());
    }

    @Test
    void successWithIp_writesOnlyExtraInfo() {
        service.analyzeAndPersist(request(Outcome.SUCCESS).addExtraInfo(kv("ip", IP)).build());

        assertEquals("{\"ip\":\"" + IP + "\"}", capturedExtraInfo());
    }

    @Test
    void successWithoutExtraInfo_writesNoExtraInfoColumn() {
        service.analyzeAndPersist(request(Outcome.SUCCESS).build());

        assertFalse(capturedInsert().getData().getFieldsMap().containsKey("ExtraInfo"));
    }

    @Test
    void failure_requestEntryNamedErrMsg_doesNotReplaceRealError() {
        service.analyzeAndPersist(request(Outcome.FAILURE)
                .addExtraInfo(kv("err_msg", "spoofed"))
                .addExtraInfo(kv("ip", IP))
                .build());

        assertEquals("{\"err_msg\":\"" + ERROR + "\",\"ip\":\"" + IP + "\"}", capturedExtraInfo());
    }
}
