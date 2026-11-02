package org.pgmbim.audit.startup;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.pgmbim.audit.grpc.AuditGrpcServiceImpl;
import org.pgmbim.audit.grpc.VersionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    // Answers the kubelet's gRPC probes; registered without interceptors because probes carry no metadata.
    private final HealthStatusManager health = new HealthStatusManager();

    private Server server;
    private Thread keepAliveThread;
    private final int grpcPort;
    private final AuditGrpcServiceImpl auditGrpcService;
    private final VersionServiceImpl  versionService;

    private volatile boolean running = false;

    public GrpcServerLifecycle(
            @Value("${server.port:50054}") int grpcPort,
            AuditGrpcServiceImpl auditGrpcService, VersionServiceImpl versionService
    ) {
        this.grpcPort = grpcPort;
        this.auditGrpcService = auditGrpcService;
        this.versionService = versionService;
    }

    @Override
    public void start() {
        try {
            log.info("Starting gRPC server on port {}", grpcPort);
            server = ServerBuilder
                    .forPort(grpcPort)
                    .addService(auditGrpcService)
                    .addService(health.getHealthService())
                    .addService(versionService)
                    .addService(ProtoReflectionServiceV1.newInstance())
                    .build()
                    .start();


            keepAliveThread = new Thread(() -> {
                try {
                    server.awaitTermination();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.debug("gRPC server awaitTermination interrupted");
                }
            });

            keepAliveThread.setName("grpc-server-keepalive");
            // Keep JVM alive for this non-web service process.
            keepAliveThread.setDaemon(false);
            keepAliveThread.start();

            health.setStatus("", ServingStatus.SERVING);
            running = true;
            log.info("Audit gRPC server started. port={}", grpcPort);

        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        // Report NOT_SERVING as soon as shutdown begins; the status cannot change afterwards.
        health.enterTerminalState();
        if (server != null) {
            log.info("Stopping gRPC server");
            server.shutdownNow();
        }
        if (keepAliveThread != null && keepAliveThread.isAlive()) {
            keepAliveThread.interrupt();
        }
        running = false;
    }


    @Override
    public boolean isRunning() {
        return running;
    }


    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
