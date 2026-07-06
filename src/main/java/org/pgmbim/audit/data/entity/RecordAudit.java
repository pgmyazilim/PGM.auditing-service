package org.pgmbim.audit.data.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

import java.time.Instant;

public record RecordAudit(
        @DasField("RecordAuditId")
        Long id,

        @DasField("ModifiedAtUtc")
        Instant version,

        @DasField("TrackedTableId")
        Integer trackedTableId,

        @DasField("RecordId")
        String recordId,

        @DasField("OperationType")
        String operationType,

        @DasField("OccurredAtUtc")
        Instant occurredAtUtc,

        @DasField("ActorUserId")
        Integer actorUserId,

        @DasField("SessionId")
        Long sessionId,

        @DasField("ActionLogId")
        Long operationLogId,

        @DasField("RecordValues")
        String recordValues,

        @DasField("ExtraInfo")
        String extraInfo
) {
}
