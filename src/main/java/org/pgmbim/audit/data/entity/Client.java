package org.pgmbim.audit.data.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

public record Client(
        @DasField("ClientId")
        Integer id,

        @DasField("ClientKey")
        String clientKey,

        @DasField("Name")
        String name
) {
}
