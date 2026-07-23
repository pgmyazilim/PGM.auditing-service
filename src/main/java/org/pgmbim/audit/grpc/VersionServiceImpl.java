package org.pgmbim.audit.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.pgmbim.grpc.aaa.audit.GetVersionResponse;
import org.pgmbim.grpc.aaa.audit.VersionServiceGrpc;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VersionServiceImpl extends VersionServiceGrpc.VersionServiceImplBase {

    private static final String VERSION = "c225708faad3ac492dee860642d52279e9701975"; //Son commit revison number.

    @Override
    public void getVersion(Empty request, StreamObserver<GetVersionResponse> responseObserver) {
        log.debug("event=grpc.version.received");
        responseObserver.onNext(GetVersionResponse.newBuilder()
                .setVersion(VERSION)
                .build());
        responseObserver.onCompleted();
    }
}
