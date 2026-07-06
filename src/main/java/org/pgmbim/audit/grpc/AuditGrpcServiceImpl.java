package org.pgmbim.audit.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pgmbim.audit.application.model.AuditPersistenceResult;
import org.pgmbim.audit.application.service.AuditAnalyzeAndPersistService;
import org.pgmbim.grpc.aaa.audit.AnalyzeAndPersistRequest;
import org.pgmbim.grpc.aaa.audit.AnalyzeAndPersistResponse;
import org.pgmbim.grpc.aaa.audit.AuditServiceGrpc;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditGrpcServiceImpl extends AuditServiceGrpc.AuditServiceImplBase {

    private final AuditAnalyzeAndPersistService analyzeAndPersistService;

    @Override
    public void analyzeAndPersist(
            AnalyzeAndPersistRequest request,
            StreamObserver<AnalyzeAndPersistResponse> responseObserver
    ) {
        try {
            AuditPersistenceResult result = analyzeAndPersistService.analyzeAndPersist(request);

            AnalyzeAndPersistResponse response = AnalyzeAndPersistResponse.newBuilder()
                    .setOperationHistoryWritten(result.operationHistoryWritten())
                    .setOperationHistoryId(result.operationHistoryId() == null ? 0L : result.operationHistoryId())
                    .setRecordHistoryWrittenCount(result.recordHistoryWrittenCount())
                    .addAllRecordHistoryIds(result.recordHistoryIds())
                    .addAllWarnings(result.warnings())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("AnalyzeAndPersist failed. actionKey={}", request.getActionKey(), ex);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("AnalyzeAndPersist failed.")
                            .withCause(ex)
                            .asRuntimeException()
            );
        } finally {
            MDC.clear();
        }
    }
}
