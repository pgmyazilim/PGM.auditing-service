package org.pgmbim.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.das.DataAccessServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the DAS (Data Access Service) gRPC client. Replaces the former
 * {@code DasClientStarter} auto-configuration: it builds a long-lived plaintext
 * channel from {@code das-grpc.host}/{@code das-grpc.port}, exposes the blocking
 * stub, and provides the {@link DasDataMapper} (with its JSR-310 aware
 * {@link ObjectMapper}) used to map DAS rows onto records.
 */
@Configuration
public class DasClientConfig {

    @Bean(destroyMethod = "shutdown")
    ManagedChannel dasManagedChannel(
            @Value("${das-grpc.host}") String host,
            @Value("${das-grpc.port}") int port
    ) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    DataAccessServiceGrpc.DataAccessServiceBlockingStub dasBlockingStub(ManagedChannel dasManagedChannel) {
        return DataAccessServiceGrpc.newBlockingStub(dasManagedChannel);
    }

    /**
     * Mirrors the starter's {@code dasDataRowMapperObjectMapper}: this was the only
     * {@code ObjectMapper} bean in the context, consumed both by {@link DasDataMapper}
     * and by application services. {@code @ConditionalOnMissingBean} preserves that
     * "provide one unless the app already defines its own" semantics.
     */
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper dasObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    DasDataMapper dasDataMapper(ObjectMapper objectMapper) {
        return new DasDataMapper(objectMapper);
    }
}
