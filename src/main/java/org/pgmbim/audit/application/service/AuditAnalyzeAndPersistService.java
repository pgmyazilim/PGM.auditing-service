package org.pgmbim.audit.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pgmbim.audit.application.model.AuditPersistenceResult;
import org.pgmbim.audit.data.entity.Action;
import org.pgmbim.audit.data.entity.ActionLog;
import org.pgmbim.audit.data.entity.RecordAudit;
import org.pgmbim.audit.data.entity.SessionDto;
import org.pgmbim.audit.data.entity.TrackedTable;
import org.pgmbim.das.client.starter.mapper.DasDataMapper;
import org.pgmbim.grpc.aaa.audit.AnalyzeAndPersistRequest;
import org.pgmbim.grpc.aaa.audit.ErrorDetail;
import org.pgmbim.grpc.aaa.audit.KeyValue;
import org.pgmbim.grpc.aaa.audit.OperationType;
import org.pgmbim.grpc.aaa.audit.Outcome;
import org.pgmbim.grpc.aaa.audit.RecordChange;
import org.pgmbim.grpc.das.DataAccessServiceGrpc;
import org.pgmbim.grpc.das.DataResponse;
import org.pgmbim.grpc.das.InsertRequest;
import org.pgmbim.grpc.das.SelectRequest;
import org.pgmbim.grpc.das.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.and;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.eq;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.fieldMap;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.insert;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.ok;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.page;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.select;
import static org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper.typedInt;

@Service
@RequiredArgsConstructor
public class AuditAnalyzeAndPersistService {

    private static final Logger log = LoggerFactory.getLogger(AuditAnalyzeAndPersistService.class);
    private static final String ACTIONS_TABLE = "Actions";
    private static final String ACTION_LOGS_TABLE = "ActionLogs";
    private static final String TRACKED_TABLES_TABLE = "TrackedTables";
    private static final String RECORD_AUDITS_TABLE = "RecordAudits";
    private static final String SESSIONS_TABLE = "Sessions";
    private static final Pattern TRACKING_SPLIT_PATTERN = Pattern.compile("[,;|\\s]+");
    private static final String ERROR_KEY = "err_msg";
    private static final Set<String> INSERT_MARKERS = Set.of("I", "INSERT");
    private static final Set<String> UPDATE_MARKERS = Set.of("U", "UPDATE");
    private static final Set<String> SELECT_MARKERS = Set.of("S", "SELECT");
    private static final Set<String> DELETE_MARKERS = Set.of("D", "DELETE");

    private final DataAccessServiceGrpc.DataAccessServiceBlockingStub dasClient;
    private final DasDataMapper dataMapper;
    private final ObjectMapper objectMapper;


    public AuditPersistenceResult analyzeAndPersist(AnalyzeAndPersistRequest request) {
        List<String> warnings = new ArrayList<>();
        SessionContext sessionContext = resolveSessionContext(request, warnings);
        populateMdc(request, sessionContext);

        if (request.getDryRun()) {
            warnings.add("No persistent write was made because dry_run=true.");
            log.warn("No persistent write was made because dry_run=true.");
            return new AuditPersistenceResult(false, null, List.of(), warnings);
        }

        Optional<Action> actionOptional = findAction(request.getActionKey());
        if (actionOptional.isEmpty()) {
            warnings.add("Action not found. actionKey=" + request.getActionKey());
            log.warn("Action not found. actionKey={}", request.getActionKey());
            return new AuditPersistenceResult(false, null, List.of(), warnings);
        }

        Action action = actionOptional.get();
        if (!shouldWriteActionLog(action, request.getOutcome())) {
            warnings.add("Action log not inserted. actionKey=" + request.getActionKey());
            log.warn("Action log not inserted. actionKey={}", request.getActionKey());
            return new AuditPersistenceResult(false, null, List.of(), warnings);
        }

        Instant now = Instant.now();
        Instant occurredAtUtc = computeOccurredAt(now, request.getExecutedMs());
        String actionExtraInfo = buildActionExtraInfo(request);
        Long actionLogId = insertActionLog(action, request, sessionContext.sessionId(), now, occurredAtUtc, actionExtraInfo);

        if (request.getOutcome() != Outcome.SUCCESS) {
            log.warn("Action log id: {}, actionKey: {}, outcome: {}, error: {}", actionLogId, request.getActionKey(), request.getOutcome(), request.getError());
            return new AuditPersistenceResult(true, actionLogId, List.of(), warnings);
        }

        if (request.getRecordsCount() == 0) {
            log.warn("No records to audit. actionKey={}, outcome={}", request.getActionKey(), request.getOutcome());
            return new AuditPersistenceResult(true, actionLogId, List.of(), warnings);
        }

        List<Long> recordAuditIds = new ArrayList<>();
        for (RecordChange recordChange : request.getRecordsList()) {
            Optional<TrackedTable> trackedTableOptional = findTrackedTable(recordChange, warnings);
            if (trackedTableOptional.isEmpty()) {
                log.warn("Record audit not inserted. tableId={}, recordId={}", recordChange.getTableId(), recordChange.getRecordId());
                return new AuditPersistenceResult(true, actionLogId, recordAuditIds, warnings);
            }

            TrackedTable trackedTable = trackedTableOptional.get();
            boolean actorWriteEnabled = supportsOperation(trackedTable.actorTrackingTypes(), request.getOperationType());
            boolean recordWriteEnabled = supportsOperation(trackedTable.recordTrackingTypes(), request.getOperationType());

            if (!actorWriteEnabled && !recordWriteEnabled) {
                log.info("Record audit skipped because tracking is disabled. table={}, operation={}, actorTrackingTypes={}, recordTrackingTypes={}",
                        trackedTable.name(), request.getOperationType(), trackedTable.actorTrackingTypes(), trackedTable.recordTrackingTypes());
                continue;
            }

            Long recordAuditId = insertRecordAudit(
                    trackedTable,
                    recordChange,
                    request,
                    sessionContext.sessionId(),
                    now,
                    occurredAtUtc,
                    actionLogId,
                    actorWriteEnabled,
                    recordWriteEnabled
            );
            recordAuditIds.add(recordAuditId);
        }
        log.info("Record audit ids: {}", recordAuditIds);
        return new AuditPersistenceResult(true, actionLogId, recordAuditIds, warnings);
    }

    private void populateMdc(AnalyzeAndPersistRequest request, SessionContext sessionContext) {
        String correlationId = request.getCorrelationId();
        String sourceService = request.getSourceService();
        String serviceMethod = request.getSourceMethod().isBlank() ? "AnalyzeAndPersist" : request.getSourceMethod();
        String userId = request.getUserId() > 0 ? String.valueOf(request.getUserId()) : "";
        String sessionKey = sessionContext.sessionKey() == null ? "" : sessionContext.sessionKey();

        MDC.put("correlation_id", correlationId);
        MDC.put("service_method", serviceMethod);
        MDC.put("source_service", sourceService);
        MDC.put("user_id", userId);
        MDC.put("session_key", sessionKey);

        MDC.put("corrId", correlationId);
        MDC.put("userId", userId);
        MDC.put("sessionKey", sessionKey);
    }

    private SessionContext resolveSessionContext(AnalyzeAndPersistRequest request, List<String> warnings) {
        Optional<String> sessionKey = Optional.ofNullable(request.getSessionKey())
                .filter(value -> !value.isBlank());
        if (sessionKey.isEmpty()) {
            return new SessionContext(null, null);
        }

        Optional<SessionDto> session = findSessionByKey(sessionKey.get());
        if (session.isPresent()) {
            SessionDto value = session.get();
            return new SessionContext(value.id() == null ? null : value.id().longValue(), value.sessionKey());
        }

        warnings.add("Session not found. sessionKey=" + sessionKey.get());
        return new SessionContext(null, sessionKey.get());
    }

    private Optional<SessionDto> findSessionByKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }

        try {
            SelectRequest request = select(SESSIONS_TABLE)
                    .setFilterGroup(and(eq("SessionKey", sessionKey)))
                    .setPagination(page(1, 0))
                    .build();

            DataResponse response = dasClient.select(request);
            ok(response);
            return dataMapper.mapFirstOptional(response, SessionDto.class);
        } catch (RuntimeException ex) {
            log.debug("Session lookup failed. sessionKey={}", sessionKey, ex);
            return Optional.empty();
        }
    }

    private Optional<Action> findAction(String actionKey) {
        return findActionByKey(actionKey);
    }

    private Optional<Action> findActionByKey(String actionKey) {
        try {
            SelectRequest request = select(ACTIONS_TABLE)
                    .setFilterGroup(and(eq("ActionKey", actionKey)))
                    .setPagination(page(1, 0))
                    .build();

            DataResponse response = dasClient.select(request);
            ok(response);
            return dataMapper.mapFirstOptional(response, Action.class);
        } catch (RuntimeException ex) {
            log.debug("Action lookup failed. actionKey={}", actionKey, ex);
            return Optional.empty();
        }
    }

    private boolean shouldWriteActionLog(Action action, Outcome outcome) {
        if (outcome == Outcome.SUCCESS) {
            return action.logOnSuccess();
        }
        if (outcome == Outcome.FAILURE) {
            return action.logOnFailure();
        }
        return false;
    }

    private Instant computeOccurredAt(Instant now, long executedMs) {
        if (executedMs <= 0) {
            return now;
        }
        return now.minusMillis(executedMs);
    }

    private String buildActionExtraInfo(AnalyzeAndPersistRequest request) {
        if (request.getOutcome() == Outcome.FAILURE) {
            return toJson(Map.of(ERROR_KEY, buildErrorMessage(request.getError())));
        }
        return toJson(keyValueListToMap(request.getExtraInfoList()));
    }

    private String buildErrorMessage(ErrorDetail error) {
        if (error == null) {
            return "Unknown error";
        }

        if (!error.getPublicMessage().isBlank()) {
            return error.getPublicMessage();
        }

        if (!error.getRootCauseClass().isBlank()) {
            if (!error.getSqlState().isBlank()) {
                return error.getRootCauseClass() + " [sqlState=" + error.getSqlState() + ", sqlErrorCode=" + error.getSqlErrorCode() + "]";
            }
            return error.getRootCauseClass();
        }

        if (error.getDetailsCount() > 0) {
            KeyValue detail = error.getDetails(0);
            if (!detail.getValue().isBlank()) {
                return detail.getKey() + ": " + detail.getValue();
            }
            return detail.getKey();
        }

        if (!error.getSqlState().isBlank()) {
            return "SQL state: " + error.getSqlState() + ", code: " + error.getSqlErrorCode();
        }

        return "Unknown error";
    }

    private Long insertActionLog(
            Action action,
            AnalyzeAndPersistRequest request,
            Long sessionId,
            Instant now,
            Instant occurredAtUtc,
            String extraInfo
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("RowVersionUtc", now);
        data.put("ActionId", action.actionId());
        if (sessionId != null && sessionId > 0) {
            data.put("SessionId", sessionId);
        }
        data.put("OccurredAtUtc", occurredAtUtc);
        data.put("IsSuccess", request.getOutcome() == Outcome.SUCCESS);
        if (!extraInfo.isBlank()) {
            data.put("ExtraInfo", extraInfo);
        }
        if (!request.getUserNote().isBlank()) {
            data.put("UserNote", request.getUserNote());
        }

        InsertRequest insertRequest = insert(ACTION_LOGS_TABLE)
                .setData(fieldMap(data))
                .build();

        DataResponse response = dasClient.insert(insertRequest);
        ok(response);

        Long actionLogId = extractGeneratedId(response, "ActionLogId");
        if (actionLogId != null) {
            return actionLogId;
        }

        return dataMapper.mapFirstOptional(response, ActionLog.class)
                .map(ActionLog::id)
                .orElseThrow(() -> new IllegalStateException("ActionLogId was not generated."));
    }

    private Optional<TrackedTable> findTrackedTable(RecordChange recordChange, List<String> warnings) {
        if (!recordChange.getTableName().isBlank()) {
            Optional<TrackedTable> trackedTable = findTrackedTableByName(recordChange.getTableName());
            if (trackedTable.isPresent()) {
                return trackedTable;
            }

            warnings.add("TrackedTable not found. tableName=" + recordChange.getTableName() + ", recordId=" + recordChange.getRecordId());
            return Optional.empty();
        }

        int trackedTableId = recordChange.getTableId();
        if (trackedTableId <= 0) {
            warnings.add("Invalid tracked table id. tableId=" + trackedTableId + ", recordId=" + recordChange.getRecordId());
            return Optional.empty();
        }

        Optional<TrackedTable> trackedTable = findTrackedTableById(trackedTableId);

        if (trackedTable.isEmpty()) {
            warnings.add("TrackedTable not found. tableId=" + trackedTableId + ", recordId=" + recordChange.getRecordId());
        }
        return trackedTable;
    }

    private Optional<TrackedTable> findTrackedTableByName(String tableName) {
        try {
            SelectRequest request = select(TRACKED_TABLES_TABLE)
                    .setFilterGroup(and(eq("Name", tableName)))
                    .setPagination(page(1, 0))
                    .build();

            DataResponse response = dasClient.select(request);
            ok(response);
            return dataMapper.mapFirstOptional(response, TrackedTable.class);
        } catch (RuntimeException ex) {
            log.debug("Tracked table lookup failed. tableName={}", tableName, ex);
            return Optional.empty();
        }
    }

    private Optional<TrackedTable> findTrackedTableById(int trackedTableId) {
        try {
            SelectRequest request = select(TRACKED_TABLES_TABLE)
                    .setFilterGroup(and(eq("TrackedTableId", typedInt(trackedTableId))))
                    .setPagination(page(1, 0))
                    .build();

            DataResponse response = dasClient.select(request);
            ok(response);
            return dataMapper.mapFirstOptional(response, TrackedTable.class);
        } catch (RuntimeException ex) {
            log.debug("Tracked table lookup failed. trackedTableId={}", trackedTableId, ex);
            return Optional.empty();
        }
    }

    private boolean supportsOperation(String configuredTrackingTypes, OperationType operationType) {
        if (configuredTrackingTypes == null || configuredTrackingTypes.isBlank()) {
            return false;
        }

        Set<String> markers = markersFor(operationType);
        if (markers.isEmpty()) {
            return false;
        }

        String normalized = configuredTrackingTypes
                .replace("[", " ")
                .replace("]", " ")
                .replace("{", " ")
                .replace("}", " ")
                .replace("\"", " ")
                .replace("'", " ");

        String[] tokens = TRACKING_SPLIT_PATTERN.split(normalized);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            String upperToken = token.toUpperCase(Locale.ROOT).trim();
            if (markers.contains(upperToken)) {
                return true;
            }

            for (String marker : markers) {
                if (marker.length() == 1 && upperToken.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> markersFor(OperationType operationType) {
        return switch (operationType) {
            case INSERT -> INSERT_MARKERS;
            case UPDATE -> UPDATE_MARKERS;
            case SELECT -> SELECT_MARKERS;
            case DELETE -> DELETE_MARKERS;
            default -> Set.of();
        };
    }

    private Long insertRecordAudit(
            TrackedTable trackedTable,
            RecordChange recordChange,
            AnalyzeAndPersistRequest request,
            Long sessionId,
            Instant now,
            Instant occurredAtUtc,
            Long actionLogId,
            boolean actorWriteEnabled,
            boolean recordWriteEnabled
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("RowVersionUtc", now);
        data.put("TrackedTableId", trackedTable.id());
        data.put("RecordId", recordChange.getRecordId());
        data.put("OperationType", operationCode(request.getOperationType()));
        data.put("OccurredAtUtc", occurredAtUtc);
        data.put("OperationLogId", actionLogId);

        if (actorWriteEnabled) {
            toOptionalInt(request.getUserId()).ifPresent(userId -> data.put("ActorUserId", userId));
            if (sessionId != null && sessionId > 0) {
                data.put("SessionId", sessionId);
            }
        }

        if (recordWriteEnabled && !recordChange.getSnapshot().isBlank()) {
            data.put("RecordValues", recordChange.getSnapshot());
        }

        String recordExtraInfo = toJson(keyValueListToMap(recordChange.getExtraInfoList()));
        if (!recordExtraInfo.isBlank()) {
            data.put("ExtraInfo", recordExtraInfo);
        }

        return tryInsertRecordAudit(data)
                .orElseThrow(() -> new IllegalStateException("RecordAudit insert failed."));
    }

    private Optional<Long> tryInsertRecordAudit(Map<String, Object> data) {
        try {
            InsertRequest request = insert(RECORD_AUDITS_TABLE)
                    .setData(fieldMap(data))
                    .build();

            DataResponse response = dasClient.insert(request);
            ok(response);

            Long generatedId = extractGeneratedId(response, "RecordAuditId");
            if (generatedId != null) {
                return Optional.of(generatedId);
            }

            return dataMapper.mapFirstOptional(response, RecordAudit.class).map(RecordAudit::id);
        } catch (RuntimeException ex) {
            log.warn("Record audit insert failed. table={}, data={}", RECORD_AUDITS_TABLE, data, ex);
            return Optional.empty();
        }
    }

    private Long extractGeneratedId(DataResponse response, String preferredKey) {
        TypedValue preferred = response.getGeneratedKeysMap().get(preferredKey);
        if (preferred != null) {
            return typedValueToLong(preferred);
        }

        if (!response.getGeneratedKeysMap().isEmpty()) {
            for (TypedValue value : response.getGeneratedKeysMap().values()) {
                Long converted = typedValueToLong(value);
                if (converted != null) {
                    return converted;
                }
            }
        }

        return null;
    }

    private Long typedValueToLong(TypedValue value) {
        if (value == null) {
            return null;
        }

        return switch (value.getKindCase()) {
            case INT64_VALUE -> value.getInt64Value();
            case INT32_VALUE -> (long) value.getInt32Value();
            case UINT64_VALUE -> value.getUint64Value();
            case UINT32_VALUE -> Integer.toUnsignedLong(value.getUint32Value());
            case STRING_VALUE -> parseLong(value.getStringValue());
            case DECIMAL_VALUE -> parseDecimalLong(value.getDecimalValue());
            default -> null;
        };
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseDecimalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).longValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Optional<Integer> toOptionalInt(long value) {
        if (value <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Math.toIntExact(value));
        } catch (ArithmeticException ex) {
            return Optional.empty();
        }
    }

    private String operationCode(OperationType operationType) {
        return switch (operationType) {
            case INSERT -> "I";
            case UPDATE -> "U";
            case SELECT -> "S";
            case DELETE -> "D";
            default -> operationType.name();
        };
    }

    private Map<String, String> keyValueListToMap(List<KeyValue> keyValues) {
        Map<String, String> result = new LinkedHashMap<>();
        for (KeyValue keyValue : keyValues) {
            if (keyValue.getKey().isBlank()) {
                continue;
            }
            result.put(keyValue.getKey(), keyValue.getValue());
        }
        return result;
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize map to JSON.", ex);
            return "";
        }
    }

    private record SessionContext(
            Long sessionId,
            String sessionKey
    ) {
    }
}
