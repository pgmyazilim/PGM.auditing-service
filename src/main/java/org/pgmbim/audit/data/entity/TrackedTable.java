package org.pgmbim.audit.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

import java.time.Instant;

public record TrackedTable(
        @DasField("TrackedTableId")
        Integer id,

        @DasField("RowVersionUtc")
        Instant version,

        @DasField("Name")
        String name,

        @DasField("Description")
        String description,

        @DasField("ActorTrackingTypes")
        String actorTrackingTypes,

        @DasField("RecordTrackingTypes")
        String recordTrackingTypes
) {
}
