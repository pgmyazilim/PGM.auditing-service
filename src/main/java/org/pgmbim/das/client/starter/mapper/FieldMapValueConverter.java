package org.pgmbim.das.client.starter.mapper;

import com.google.protobuf.util.Timestamps;
import org.pgmbim.das.client.starter.grpc.DasGrpcQueryHelper;
import org.pgmbim.grpc.das.DataList;
import org.pgmbim.grpc.das.FieldMap;
import org.pgmbim.grpc.das.TypedValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FieldMapValueConverter {

    private FieldMapValueConverter() {
    }

    static Map<String, Object> fieldMapToMap(FieldMap fieldMap) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, TypedValue> entry : fieldMap.getFieldsMap().entrySet()) {
            result.put(entry.getKey(), typedValueToObject(entry.getValue()));
        }
        return result;
    }

    static FieldMap mapToFieldMap(Map<String, Object> values) {
        return DasGrpcQueryHelper.fieldMap(values);
    }

    private static Object typedValueToObject(TypedValue value) {
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
            case LIST_VALUE -> dataListToObject(value.getListValue());
        };
    }

    private static List<Object> dataListToObject(DataList listValue) {
        ArrayList<Object> result = new ArrayList<>(listValue.getValuesCount());
        for (TypedValue value : listValue.getValuesList()) {
            result.add(typedValueToObject(value));
        }
        return result;
    }
}
