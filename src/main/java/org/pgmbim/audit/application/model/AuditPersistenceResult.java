package org.pgmbim.audit.application.model;

import java.util.List;

public record AuditPersistenceResult(
        boolean operationHistoryWritten,
        Long operationHistoryId,
        List<Long> recordHistoryIds,
        List<String> warnings
) {
    public int recordHistoryWrittenCount() {
        return recordHistoryIds.size();
    }
}
