package org.pgmbim.audit.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pgmbim.grpc.aaa.audit.GetVersionResponse;
import org.pgmbim.grpc.aaa.audit.VersionServiceGrpc;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


@Service
@Slf4j
@RequiredArgsConstructor
public class VersionServiceImpl extends VersionServiceGrpc.VersionServiceImplBase {

    private static final ZoneId LOCAL_ZONE = ZoneId.of("Europe/Nicosia");
    private static final DateTimeFormatter VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final BuildProperties buildProperties;

    @Override
    public void getVersion(Empty request, StreamObserver<GetVersionResponse> responseObserver) {
        log.debug("event=grpc.version.received");
        String version = ZonedDateTime.ofInstant(buildProperties.getTime(), LOCAL_ZONE).format(VERSION_FORMATTER);
        responseObserver.onNext(GetVersionResponse.newBuilder()
                .setVersion(version)
                .build());
        responseObserver.onCompleted();
    }
}
