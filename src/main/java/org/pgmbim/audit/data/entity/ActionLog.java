package org.pgmbim.audit.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

import java.time.Instant;

public record ActionLog(
        @DasField("ActionLogId")
        Long id,

        @DasField("RowVersionUtc")
        Instant version,

        @DasField("ActionId")
        Integer actionId,

        @DasField("SessionId")
        Long sessionId,

        @DasField("OccurredAtUtc")
        Instant occurredAtUtc,

        @DasField("IsSuccess")
        Boolean isSuccess,

        @DasField("ExtraInfo")
        String extraInfo,

        @DasField("UserNote")
        String userNote
) {
}
