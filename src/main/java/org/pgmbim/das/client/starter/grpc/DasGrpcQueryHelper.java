package org.pgmbim.das.client.starter.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.Timestamps;
import org.pgmbim.grpc.das.AggregateFunction;
import org.pgmbim.grpc.das.AggregateFunctionType;
import org.pgmbim.grpc.das.BatchResponse;
import org.pgmbim.grpc.das.ColumnSelection;
import org.pgmbim.grpc.das.DataList;
import org.pgmbim.grpc.das.DataResponse;
import org.pgmbim.grpc.das.DeleteRequest;
import org.pgmbim.grpc.das.FieldMap;
import org.pgmbim.grpc.das.Filter;
import org.pgmbim.grpc.das.FilterGroup;
import org.pgmbim.grpc.das.FilterOperator;
import org.pgmbim.grpc.das.InsertRequest;
import org.pgmbim.grpc.das.JoinClause;
import org.pgmbim.grpc.das.JoinCondition;
import org.pgmbim.grpc.das.JoinType;
import org.pgmbim.grpc.das.LogicalOperator;
import org.pgmbim.grpc.das.NullValue;
import org.pgmbim.grpc.das.OrderBy;
import org.pgmbim.grpc.das.Pagination;
import org.pgmbim.grpc.das.ProcedureRequest;
import org.pgmbim.grpc.das.ProcedureResponse;
import org.pgmbim.grpc.das.QueryOptions;
import org.pgmbim.grpc.das.RawQueryRequest;
import org.pgmbim.grpc.das.ResponseStatus;
import org.pgmbim.grpc.das.SelectRequest;
import org.pgmbim.grpc.das.SortDirection;
import org.pgmbim.grpc.das.TypedValue;
import org.pgmbim.grpc.das.UpdateRequest;
import org.pgmbim.grpc.das.ValueType;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DasGrpcQueryHelper {

    private static final String DEFAULT_SINGLE_VALUE_FIELD = "value";
    private static final DateTimeFormatter SQL_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final NullValue PROTO_NULL_VALUE = NullValue.NULL_VALUE;

    private DasGrpcQueryHelper() {
    }

    public static boolean ok(DataResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response is null.");
        }
        if (response.getStatus() == ResponseStatus.ERROR) {
            throw new IllegalArgumentException(response.getErrorMessage());
        }
        return response.getStatus() == ResponseStatus.SUCCESS;
    }

    public static boolean hasData(DataResponse response) {
        return response != null && response.getRecordsCount() > 0;
    }

    public static boolean hasGeneratedKeys(DataResponse response) {
        return response != null && response.getGeneratedKeysCount() > 0;
    }

    public static Map<String, TypedValue> generatedKeys(DataResponse response) {
        if (response == null || response.getGeneratedKeysCount() == 0) {
            return Map.of();
        }
        return response.getGeneratedKeysMap();
    }

    public static TypedValue generatedKey(DataResponse response, String key) {
        if (response == null || key == null || key.isBlank() || !response.containsGeneratedKeys(key)) {
            return null;
        }
        return response.getGeneratedKeysOrThrow(key);
    }

    public static <T> T generatedKey(DataResponse response, String key, Class<T> targetType) {
        return typedValue(generatedKey(response, key), targetType);
    }

    public static boolean ok(BatchResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response is null.");
        }
        if (response.getStatus() == ResponseStatus.ERROR) {
            throw new IllegalArgumentException(response.getErrorMessage());
        }
        return response.getStatus() == ResponseStatus.SUCCESS;
    }

    public static boolean ok(ProcedureResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response is null.");
        }
        if (response.getStatus() == ResponseStatus.ERROR) {
            throw new IllegalArgumentException(response.getErrorMessage());
        }
        return response.getStatus() == ResponseStatus.SUCCESS;
    }

    public static FilterGroup and(Filter... filters) {
        FilterGroup.Builder b = FilterGroup.newBuilder().setOperator(LogicalOperator.AND);
        if (filters != null) {
            for (Filter f : filters) {
                if (f == null) {
                    continue;
                }
                b.addFilters(f);
            }
        }
        return b.build();
    }

    public static FilterGroup andGroups(FilterGroup... groups) {
        return group(LogicalOperator.AND, null, groups);
    }

    public static FilterGroup or(Filter... filters) {
        FilterGroup.Builder b = FilterGroup.newBuilder().setOperator(LogicalOperator.OR);
        if (filters != null) {
            for (Filter f : filters) {
                if (f == null) {
                    continue;
                }
                b.addFilters(f);
            }
        }
        return b.build();
    }

    public static FilterGroup orGroups(FilterGroup... groups) {
        return group(LogicalOperator.OR, null, groups);
    }

    public static FilterGroup group(LogicalOperator operator, List<Filter> filters, List<FilterGroup> groups) {
        FilterGroup.Builder b = FilterGroup.newBuilder().setOperator(operator);
        if (filters != null) {
            for (Filter filter : filters) {
                if (filter == null) {
                    continue;
                }
                b.addFilters(filter);
            }
        }
        if (groups != null) {
            for (FilterGroup group : groups) {
                if (group == null) {
                    continue;
                }
                b.addNestedGroups(group);
            }
        }
        return b.build();
    }

    public static FilterGroup group(LogicalOperator operator, Filter[] filters, FilterGroup[] groups) {
        FilterGroup.Builder b = FilterGroup.newBuilder().setOperator(operator);
        if (filters != null) {
            for (Filter filter : filters) {
                if (filter == null) {
                    continue;
                }
                b.addFilters(filter);
            }
        }
        if (groups != null) {
            for (FilterGroup group : groups) {
                if (group == null) {
                    continue;
                }
                b.addNestedGroups(group);
            }
        }
        return b.build();
    }

    public static Filter filter(String column, FilterOperator op, String value) {
        return filter(column, op, value == null ? null : toTypedValue(value));
    }

    public static Filter filter(String column, FilterOperator op, List<String> values) {
        return filterTyped(column, op, values);
    }

    public static Filter filter(String column, FilterOperator op, TypedValue value) {
        Filter.Builder b = Filter.newBuilder().setColumn(column).setOperator(op);
        if (value != null) {
            b.setValue(value);
        }
        return b.build();
    }

    public static Filter filterTyped(String column, FilterOperator op, List<?> values) {
        Filter.Builder b = Filter.newBuilder().setColumn(column).setOperator(op);
        if (values == null || values.isEmpty()) {
            return b.build();
        }
        for (Object value : values) {
            b.addValues(toTypedValue(value));
        }
        return b.build();
    }

    public static TypedValue typedValue(Object value) {
        return toTypedValue(value);
    }

    public static Object typedValue(TypedValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getKindCase()) {
            case NULL_VALUE, KIND_NOT_SET -> null;
            case BOOL_VALUE -> value.getBoolValue();
            case INT32_VALUE -> value.getInt32Value();
            case INT64_VALUE -> value.getInt64Value();
            case UINT32_VALUE -> Integer.toUnsignedLong(value.getUint32Value());
            case UINT64_VALUE -> value.getUint64Value();
            case FLOAT_VALUE -> value.getFloatValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case STRING_VALUE -> value.getStringValue();
            case BYTES_VALUE -> value.getBytesValue().toByteArray();
            case TIMESTAMP_VALUE -> Instant.ofEpochMilli(Timestamps.toMillis(value.getTimestampValue()));
            case DECIMAL_VALUE -> new BigDecimal(value.getDecimalValue());
            case OBJECT_VALUE -> fieldMapToMap(value.getObjectValue());
            case LIST_VALUE -> dataListToList(value.getListValue());
        };
    }

    public static <T> T typedValue(TypedValue value, Class<T> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("Target type is null.");
        }
        if (value == null) {
            return null;
        }
        if (targetType == Long.class) {
            return targetType.cast(extractLong(value));
        }
        Object rawValue = typedValue(value);
        if (rawValue == null) {
            return null;
        }
        if (targetType.isInstance(rawValue)) {
            return targetType.cast(rawValue);
        }
        if (targetType == String.class) {
            return targetType.cast(String.valueOf(rawValue));
        }
        throw new IllegalArgumentException("Cannot convert TypedValue kind " + value.getKindCase() + " to " + targetType.getName() + ".");
    }

    private static Long extractLong(TypedValue value) {
        return switch (value.getKindCase()) {
            case INT64_VALUE -> value.getInt64Value();
            case INT32_VALUE -> (long) value.getInt32Value();
            case UINT32_VALUE -> Integer.toUnsignedLong(value.getUint32Value());
            case UINT64_VALUE -> Long.parseUnsignedLong(Long.toUnsignedString(value.getUint64Value()));
            case DECIMAL_VALUE -> new BigDecimal(value.getDecimalValue()).longValueExact();
            case STRING_VALUE -> new BigDecimal(value.getStringValue().trim()).longValueExact();
            default -> throw new IllegalArgumentException("Cannot convert TypedValue kind " + value.getKindCase() + " to java.lang.Long.");
        };
    }

    public static TypedValue typedString(String value) {
        if (value == null) {
            return typedStringNull();
        }
        return TypedValue.newBuilder().setType(ValueType.STRING).setStringValue(value).build();
    }

    public static TypedValue typedBool(boolean value) {
        return TypedValue.newBuilder().setType(ValueType.BOOL).setBoolValue(value).build();
    }

    public static TypedValue typedInt(int value) {
        return TypedValue.newBuilder().setType(ValueType.INT32).setInt32Value(value).build();
    }

    public static List<TypedValue> typedIntList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<TypedValue> result = new ArrayList<>(values.size());
        for (Integer value : values) {
            result.add(toTypedValue(value));
        }
        return result;
    }

    public static TypedValue typedLong(long value) {
        return TypedValue.newBuilder().setType(ValueType.INT64).setInt64Value(value).build();
    }

    public static List<TypedValue> typedLongList(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<TypedValue> result = new ArrayList<>(values.size());
        for (Long value : values) {
            result.add(toTypedValue(value));
        }
        return result;
    }

    public static TypedValue typedFloat(float value) {
        return TypedValue.newBuilder().setType(ValueType.FLOAT).setFloatValue(value).build();
    }

    public static TypedValue typedDouble(double value) {
        return TypedValue.newBuilder().setType(ValueType.DOUBLE).setDoubleValue(value).build();
    }

    public static TypedValue typedDecimal(BigDecimal value) {
        if (value == null) {
            return typedDecimalNull();
        }
        return TypedValue.newBuilder().setType(ValueType.DECIMAL).setDecimalValue(value.toPlainString()).build();
    }

    public static TypedValue typedDecimal(String value) {
        if (value == null) {
            return typedDecimalNull();
        }
        return TypedValue.newBuilder().setType(ValueType.DECIMAL).setDecimalValue(value).build();
    }

    public static TypedValue typedTimestamp(Instant value) {
        if (value == null) {
            return typedTimestampNull();
        }
        return TypedValue.newBuilder().setType(ValueType.TIMESTAMP).setTimestampValue(Timestamps.fromMillis(value.toEpochMilli())).build();
    }

    public static TypedValue typedTimestamp(java.util.Date value) {
        if (value == null) {
            return typedTimestampNull();
        }
        if (value instanceof Date sqlDate) {
            return typedString(formatSqlDate(sqlDate.toLocalDate()));
        }
        return TypedValue.newBuilder().setType(ValueType.TIMESTAMP).setTimestampValue(Timestamps.fromMillis(value.getTime())).build();
    }

    public static TypedValue typedBytes(byte[] value) {
        if (value == null) {
            return typedBytesNull();
        }
        return TypedValue.newBuilder().setType(ValueType.BYTES).setBytesValue(ByteString.copyFrom(value)).build();
    }

    public static TypedValue typedObject(FieldMap value) {
        if (value == null) {
            return typedObjectNull();
        }
        return TypedValue.newBuilder().setType(ValueType.OBJECT).setObjectValue(value).build();
    }

    public static TypedValue typedList(Iterable<?> values) {
        if (values == null) {
            return typedListNull();
        }
        DataList.Builder lb = DataList.newBuilder();
        for (Object item : values) {
            lb.addValues(toTypedValue(item));
        }
        return TypedValue.newBuilder().setType(ValueType.LIST).setListValue(lb).build();
    }

    public static TypedValue typedStringNull() {
        return typedNull(ValueType.STRING);
    }

    public static TypedValue typedBoolNull() {
        return typedNull(ValueType.BOOL);
    }

    public static TypedValue typedIntNull() {
        return typedNull(ValueType.INT32);
    }

    public static TypedValue typedLongNull() {
        return typedNull(ValueType.INT64);
    }

    public static TypedValue typedFloatNull() {
        return typedNull(ValueType.FLOAT);
    }

    public static TypedValue typedDoubleNull() {
        return typedNull(ValueType.DOUBLE);
    }

    public static TypedValue typedDecimalNull() {
        return typedNull(ValueType.DECIMAL);
    }

    public static TypedValue typedTimestampNull() {
        return typedNull(ValueType.TIMESTAMP);
    }

    public static TypedValue typedBytesNull() {
        return typedNull(ValueType.BYTES);
    }

    public static TypedValue typedObjectNull() {
        return typedNull(ValueType.OBJECT);
    }

    public static TypedValue typedListNull() {
        return typedNull(ValueType.LIST);
    }

    public static Filter eq(String column, String value) {
        return filter(column, FilterOperator.EQ, value);
    }

    public static Filter eq(String column, TypedValue value) {
        return filter(column, FilterOperator.EQ, value);
    }

    public static Filter ne(String column, String value) {
        return filter(column, FilterOperator.NE, value);
    }

    public static Filter ne(String column, TypedValue value) {
        return filter(column, FilterOperator.NE, value);
    }

    public static Filter gt(String column, String value) {
        return filter(column, FilterOperator.GT, value);
    }

    public static Filter gt(String column, TypedValue value) {
        return filter(column, FilterOperator.GT, value);
    }

    public static Filter gte(String column, String value) {
        return filter(column, FilterOperator.GTE, value);
    }

    public static Filter gte(String column, TypedValue value) {
        return filter(column, FilterOperator.GTE, value);
    }

    public static Filter lt(String column, String value) {
        return filter(column, FilterOperator.LT, value);
    }

    public static Filter lt(String column, TypedValue value) {
        return filter(column, FilterOperator.LT, value);
    }

    public static Filter lte(String column, String value) {
        return filter(column, FilterOperator.LTE, value);
    }

    public static Filter lte(String column, TypedValue value) {
        return filter(column, FilterOperator.LTE, value);
    }

    public static Filter like(String column, String value) {
        return filter(column, FilterOperator.LIKE, value);
    }

    public static Filter like(String column, TypedValue value) {
        return filter(column, FilterOperator.LIKE, value);
    }

    public static Filter in(String column, List<String> values) {
        return filterTyped(column, FilterOperator.IN, values);
    }

    public static Filter typedIn(String column, List<TypedValue> values) {
        return filterTyped(column, FilterOperator.IN, values);
    }

    public static Filter notIn(String column, List<String> values) {
        return filterTyped(column, FilterOperator.NOT_IN, values);
    }

    public static Filter typedNotIn(String column, List<TypedValue> values) {
        return filterTyped(column, FilterOperator.NOT_IN, values);
    }

    public static Filter between(String column, String start, String end) {
        ArrayList<String> values = new ArrayList<>(2);
        values.add(start);
        values.add(end);
        return filterTyped(column, FilterOperator.BETWEEN, values);
    }

    public static Filter between(String column, TypedValue start, TypedValue end) {
        ArrayList<TypedValue> values = new ArrayList<>(2);
        values.add(start);
        values.add(end);
        return filterTyped(column, FilterOperator.BETWEEN, values);
    }

    public static Filter isNull(String column) {
        return filter(column, FilterOperator.IS_NULL, (TypedValue) null);
    }

    public static Filter isNotNull(String column) {
        return filter(column, FilterOperator.IS_NOT_NULL, (TypedValue) null);
    }

    public static ColumnSelection column(String column) {
        return ColumnSelection.newBuilder().setColumn(column).build();
    }

    public static ColumnSelection column(String column, String alias) {
        ColumnSelection.Builder b = ColumnSelection.newBuilder().setColumn(column);
        if (alias != null && !alias.isBlank()) {
            b.setAlias(alias);
        }
        return b.build();
    }

    public static List<ColumnSelection> columns(String... names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        ArrayList<ColumnSelection> result = new ArrayList<>(names.length);
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            result.add(column(name));
        }
        return result;
    }

    public static JoinClause join(String schema, String table, JoinType type, String onLeftColumn, String onRightColumn) {
        return join(schema, table, type, onLeftColumn, onRightColumn, null);
    }

    public static JoinClause join(String schema, String table, JoinType type, String onLeftColumn, String onRightColumn, String tableAlias) {
        JoinClause.Builder b = JoinClause.newBuilder().setSchema(schema).setTable(table).setType(type)
                .addOnConditions(JoinCondition.newBuilder()
                        .setLeftColumn(onLeftColumn)
                        .setRightColumn(onRightColumn)
                        .build());
        if (tableAlias != null && !tableAlias.isBlank()) {
            b.setTableAlias(tableAlias);
        }
        return b.build();
    }

    public static AggregateFunction aggregate(AggregateFunctionType function, String column) {
        return aggregate(function, column, null);
    }

    public static AggregateFunction aggregate(AggregateFunctionType function, String column, String alias) {
        AggregateFunction.Builder b = AggregateFunction.newBuilder().setFunction(function).setColumn(column);
        if (alias != null && !alias.isBlank()) {
            b.setAlias(alias);
        }
        return b.build();
    }

    public static OrderBy orderBy(String column) {
        return orderBy(column, SortDirection.ASC);
    }

    public static OrderBy orderBy(String column, SortDirection direction) {
        return OrderBy.newBuilder().setColumn(column).setDirection(direction).build();
    }

    public static Pagination page(int limit) {
        return page(limit, 0);
    }

    public static Pagination page(int limit, int offset) {
        return Pagination.newBuilder().setLimit(limit).setOffset(offset).build();
    }

    public static QueryOptions options(int timeoutSeconds) {
        return QueryOptions.newBuilder().setTimeoutSeconds(timeoutSeconds).build();
    }

    @SuppressWarnings("deprecation")
    public static QueryOptions options(int timeoutSeconds, boolean useCache, int cacheTtlSeconds) {
        return QueryOptions.newBuilder().setTimeoutSeconds(timeoutSeconds).setUseCache(useCache).setCacheTtlSeconds(cacheTtlSeconds).build();
    }

    public static SelectRequest.Builder select(String table) {
        return select(null, null, table);
    }

    public static SelectRequest.Builder select(String schema, String table) {
        return select(null, schema, table);
    }

    public static SelectRequest.Builder select(int moduleDatabaseId, String table) {
        return select(Integer.valueOf(moduleDatabaseId), null, table);
    }

    public static SelectRequest.Builder select(int moduleDatabaseId, String schema, String table) {
        return select(Integer.valueOf(moduleDatabaseId), schema, table);
    }

    public static InsertRequest.Builder insert(String table) {
        return insert(null, null, table);
    }

    public static InsertRequest.Builder insert(String schema, String table) {
        return insert(null, schema, table);
    }

    public static InsertRequest.Builder insert(int moduleDatabaseId, String table) {
        return insert(Integer.valueOf(moduleDatabaseId), null, table);
    }

    public static InsertRequest.Builder insert(int moduleDatabaseId, String schema, String table) {
        return insert(Integer.valueOf(moduleDatabaseId), schema, table);
    }

    public static UpdateRequest.Builder update(String table) {
        return update(null, null, table);
    }

    public static UpdateRequest.Builder update(String schema, String table) {
        return update(null, schema, table);
    }

    public static UpdateRequest.Builder update(int moduleDatabaseId, String table) {
        return update(Integer.valueOf(moduleDatabaseId), null, table);
    }

    public static UpdateRequest.Builder update(int moduleDatabaseId, String schema, String table) {
        return update(Integer.valueOf(moduleDatabaseId), schema, table);
    }

    public static DeleteRequest.Builder delete(String table) {
        return delete(null, null, table);
    }

    public static DeleteRequest.Builder delete(String schema, String table) {
        return delete(null, schema, table);
    }

    public static DeleteRequest.Builder delete(int moduleDatabaseId, String table) {
        return delete(Integer.valueOf(moduleDatabaseId), null, table);
    }

    public static DeleteRequest.Builder delete(int moduleDatabaseId, String schema, String table) {
        return delete(Integer.valueOf(moduleDatabaseId), schema, table);
    }

    public static RawQueryRequest.Builder rawSql(String sqlQuery) {
        return rawSql(null, sqlQuery);
    }

    public static RawQueryRequest.Builder rawSql(int moduleDatabaseId, String sqlQuery) {
        return rawSql(Integer.valueOf(moduleDatabaseId), sqlQuery);
    }

    public static ProcedureRequest.Builder procedure(String procedureName) {
        return procedure(null, procedureName);
    }

    public static ProcedureRequest.Builder procedure(int moduleDatabaseId, String procedureName) {
        return procedure(Integer.valueOf(moduleDatabaseId), procedureName);
    }

    public static Map<String, String> params(String key, String value, String... morePairs) {
        if (key == null) {
            throw new IllegalArgumentException("First parameter key is null.");
        }
        if (morePairs != null && morePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be key/value pairs.");
        }
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        if (morePairs != null) {
            for (int i = 0; i < morePairs.length; i += 2) {
                params.put(morePairs[i], morePairs[i + 1]);
            }
        }
        return params;
    }

    public static FieldMap fieldMap(Map<String, ?> values) {
        return fieldMap(values, null);
    }

    public static FieldMap fieldMap(Map<String, ?> values, Map<String, ValueType> nullTypes) {
        if (values == null || values.isEmpty()) {
            return FieldMap.getDefaultInstance();
        }
        FieldMap.Builder b = FieldMap.newBuilder();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            ValueType nullType = nullTypes == null ? null : nullTypes.get(key);
            b.putFields(key, toTypedValue(entry.getValue(), nullType));
        }
        return b.build();
    }

    public static FieldMap fieldMap(TypedValue value) {
        return fieldMap(DEFAULT_SINGLE_VALUE_FIELD, value);
    }

    public static FieldMap fieldMap(String key, TypedValue value) {
        if (key == null) {
            throw new IllegalArgumentException("Field key is null.");
        }
        FieldMap.Builder b = FieldMap.newBuilder();
        b.putFields(key, value == null ? toTypedValue(null) : value);
        return b.build();
    }

    public static FieldMap fieldMap(String key, Object value, Object... morePairs) {
        if (key == null) {
            throw new IllegalArgumentException("First field key is null.");
        }
        if (morePairs != null && morePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Fields must be key/value pairs.");
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(key, value);
        if (morePairs != null) {
            for (int i = 0; i < morePairs.length; i += 2) {
                values.put(String.valueOf(morePairs[i]), morePairs[i + 1]);
            }
        }
        return fieldMap(values);
    }

    public static List<String> toStringList(List<FieldMap> fieldMaps, String fieldName) {
        if (fieldMaps == null || fieldMaps.isEmpty()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>(fieldMaps.size());
        for (FieldMap fieldMap : fieldMaps) {
            if (fieldMap == null) {
                continue;
            }
            TypedValue value = fieldMap.getFieldsMap().get(fieldName);
            if (value == null) {
                continue;
            }
            String text = typedValueToString(value);
            if (text == null) {
                continue;
            }
            result.add(text);
        }
        return result;
    }

    public static List<String> toStringList(DataResponse response, String fieldName) {
        if (response == null) {
            return List.of();
        }
        return toStringList(response.getRecordsList(), fieldName);
    }

    private static String typedValueToString(TypedValue value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INT32_VALUE -> String.valueOf(value.getInt32Value());
            case INT64_VALUE -> String.valueOf(value.getInt64Value());
            case UINT32_VALUE -> String.valueOf(Integer.toUnsignedLong(value.getUint32Value()));
            case UINT64_VALUE -> String.valueOf(value.getUint64Value());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            case FLOAT_VALUE -> String.valueOf(value.getFloatValue());
            case DOUBLE_VALUE -> String.valueOf(value.getDoubleValue());
            case BYTES_VALUE -> value.getBytesValue().toStringUtf8();
            case DECIMAL_VALUE -> value.getDecimalValue();
            case TIMESTAMP_VALUE -> value.getTimestampValue().toString();
            case NULL_VALUE, KIND_NOT_SET, OBJECT_VALUE, LIST_VALUE -> null;
        };
    }

    private static Map<String, Object> fieldMapToMap(FieldMap fieldMap) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, TypedValue> entry : fieldMap.getFieldsMap().entrySet()) {
            result.put(entry.getKey(), typedValue(entry.getValue()));
        }
        return result;
    }

    private static List<Object> dataListToList(DataList listValue) {
        ArrayList<Object> result = new ArrayList<>(listValue.getValuesCount());
        for (TypedValue value : listValue.getValuesList()) {
            result.add(typedValue(value));
        }
        return result;
    }

    private static String formatSqlDate(LocalDate value) {
        return SQL_DATE_FORMATTER.format(value);
    }

    public static TypedValue typedNull(ValueType valueType) {
        ValueType resolvedType = valueType == null ? ValueType.VALUE_TYPE_UNSPECIFIED : valueType;
        return TypedValue.newBuilder().setType(resolvedType).setNullValue(PROTO_NULL_VALUE).build();
    }

    private static TypedValue toTypedValue(Object value) {
        return toTypedValue(value, null);
    }

    private static TypedValue toTypedValue(Object value, ValueType nullType) {
        if (value == null) {
            return typedNull(nullType);
        }
        if (value instanceof TypedValue v) {
            return v;
        }
        if (value instanceof FieldMap fieldMap) {
            return TypedValue.newBuilder().setType(ValueType.OBJECT).setObjectValue(fieldMap).build();
        }
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                nested.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return TypedValue.newBuilder().setType(ValueType.OBJECT).setObjectValue(fieldMap(nested)).build();
        }
        if (value instanceof Iterable<?> iterable) {
            DataList.Builder lb = DataList.newBuilder();
            for (Object item : iterable) {
                lb.addValues(toTypedValue(item));
            }
            return TypedValue.newBuilder().setType(ValueType.LIST).setListValue(lb).build();
        }
        if (value.getClass().isArray()) {
            DataList.Builder lb = DataList.newBuilder();
            int len = Array.getLength(value);
            for (int i = 0; i < len; ++i) {
                lb.addValues(toTypedValue(Array.get(value, i)));
            }
            return TypedValue.newBuilder().setType(ValueType.LIST).setListValue(lb).build();
        }
        if (value instanceof Boolean bool) {
            return TypedValue.newBuilder().setType(ValueType.BOOL).setBoolValue(bool).build();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return TypedValue.newBuilder().setType(ValueType.INT32).setInt32Value(((Number) value).intValue()).build();
        }
        if (value instanceof Long l) {
            return TypedValue.newBuilder().setType(ValueType.INT64).setInt64Value(l).build();
        }
        if (value instanceof Float f) {
            return TypedValue.newBuilder().setType(ValueType.FLOAT).setFloatValue(f).build();
        }
        if (value instanceof Double d) {
            return TypedValue.newBuilder().setType(ValueType.DOUBLE).setDoubleValue(d).build();
        }
        if (value instanceof BigDecimal decimal) {
            return TypedValue.newBuilder().setType(ValueType.DECIMAL).setDecimalValue(decimal.toPlainString()).build();
        }
        if (value instanceof BigInteger bigInteger) {
            return TypedValue.newBuilder().setType(ValueType.DECIMAL).setDecimalValue(bigInteger.toString()).build();
        }
        if (value instanceof Instant instant) {
            return TypedValue.newBuilder().setType(ValueType.TIMESTAMP).setTimestampValue(Timestamps.fromMillis(instant.toEpochMilli())).build();
        }
        if (value instanceof Date sqlDate) {
            return TypedValue.newBuilder().setType(ValueType.STRING).setStringValue(formatSqlDate(sqlDate.toLocalDate())).build();
        }
        if (value instanceof LocalDate localDate) {
            return TypedValue.newBuilder().setType(ValueType.STRING).setStringValue(formatSqlDate(localDate)).build();
        }
        if (value instanceof java.util.Date date) {
            return TypedValue.newBuilder().setType(ValueType.TIMESTAMP).setTimestampValue(Timestamps.fromMillis(date.getTime())).build();
        }
        if (value instanceof Enum<?> enumValue) {
            return TypedValue.newBuilder().setType(ValueType.STRING).setStringValue(enumValue.name()).build();
        }
        if (value instanceof byte[] bytes) {
            return TypedValue.newBuilder().setType(ValueType.BYTES).setBytesValue(ByteString.copyFrom(bytes)).build();
        }
        return TypedValue.newBuilder().setType(ValueType.STRING).setStringValue(String.valueOf(value)).build();
    }

    private static SelectRequest.Builder select(Integer moduleDatabaseId, String schema, String table) {
        SelectRequest.Builder builder = SelectRequest.newBuilder().setTable(table);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        if (schema != null) {
            builder.setSchema(schema);
        }
        return builder;
    }

    private static InsertRequest.Builder insert(Integer moduleDatabaseId, String schema, String table) {
        InsertRequest.Builder builder = InsertRequest.newBuilder().setTable(table);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        if (schema != null) {
            builder.setSchema(schema);
        }
        return builder;
    }

    private static UpdateRequest.Builder update(Integer moduleDatabaseId, String schema, String table) {
        UpdateRequest.Builder builder = UpdateRequest.newBuilder().setTable(table);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        if (schema != null) {
            builder.setSchema(schema);
        }
        return builder;
    }

    private static DeleteRequest.Builder delete(Integer moduleDatabaseId, String schema, String table) {
        DeleteRequest.Builder builder = DeleteRequest.newBuilder().setTable(table);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        if (schema != null) {
            builder.setSchema(schema);
        }
        return builder;
    }

    private static RawQueryRequest.Builder rawSql(Integer moduleDatabaseId, String sqlQuery) {
        RawQueryRequest.Builder builder = RawQueryRequest.newBuilder().setSqlQuery(sqlQuery);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        return builder;
    }

    private static ProcedureRequest.Builder procedure(Integer moduleDatabaseId, String procedureName) {
        ProcedureRequest.Builder builder = ProcedureRequest.newBuilder().setProcedureName(procedureName);
        if (moduleDatabaseId != null) {
            builder.setModuleDatabaseId(moduleDatabaseId);
        }
        return builder;
    }
}
