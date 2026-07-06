package org.pgmbim.audit.data.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

import java.time.Instant;

public record Action(
        @DasField("ActionId")
        Integer actionId,

        @DasField("ModifiedAtUtc")
        Instant rowVersionUtc,

        @DasField("ModuleId")
        Integer moduleId,

        @DasField("Name")
        String name,

        @DasField("Description")
        String description,

        @DasField("ActionKey")
        String actionKey,

        @DasField("IsActive")
        boolean isActive,

        @DasField("RequiresAuthorization")
        boolean requiresAuthorization,

        @DasField("LogOnSuccess")
        boolean logOnSuccess,

        @DasField("LogOnFailure")
        boolean logOnFailure
) {
}
