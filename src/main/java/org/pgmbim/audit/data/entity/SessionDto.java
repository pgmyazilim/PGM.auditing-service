package org.pgmbim.audit.data.entity;

import org.pgmbim.das.client.starter.mapper.DasField;

import java.time.Instant;


public record SessionDto(
        @DasField("SessionId")
        Integer id,

        @DasField("ModifiedAtUtc")
        Instant version,

        @DasField("UserId")
        Integer userId,

        @DasField("SessionKey")
        String sessionKey,

        @DasField("IsOpen")
        Boolean isOpen,

        @DasField("OpenedAtUtc")
        Instant openedAtUtc,

        @DasField("ClosedAtUtc")
        Instant closedAtUtc,

        @DasField("IsNormalClose")
        Boolean isNormalClose,

        @DasField("ClientIpAddress")
        String ipAddress,

        @DasField("ClientMacAddress")
        String macAddress,

        @DasField("ExtraInfo")
        String extraInfo
) {

}
