package org.pgmbim.das.client.starter.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pgmbim.grpc.das.DataResponse;
import org.pgmbim.grpc.das.FieldMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DasDataMapper {

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    public DasDataMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> FieldMap map(T source) {
        if (source == null) {
            return null;
        }
        if (source instanceof FieldMap fieldMap) {
            return fieldMap;
        }
        if (source instanceof Map<?, ?> sourceMap) {
            return FieldMapValueConverter.mapToFieldMap(this.normalizeMap(sourceMap));
        }
        Map<String, Object> rawMap = this.objectMapper.convertValue(source, STRING_OBJECT_MAP_TYPE);
        Map<String, Object> mapped = this.mapToDasFields(rawMap, source.getClass());
        return FieldMapValueConverter.mapToFieldMap(mapped);
    }

    public <T> FieldMap mapFirst(Iterable<T> sources) {
        if (sources == null) {
            return null;
        }
        Iterator<T> iterator = sources.iterator();
        if (iterator.hasNext()) {
            T source = iterator.next();
            return this.map(source);
        }
        return null;
    }

    public Optional<FieldMap> mapFirstOptional(Iterable<?> sources) {
        return Optional.ofNullable(this.mapFirst(sources));
    }

    public <T> List<FieldMap> mapAll(Iterable<T> sources) {
        if (sources == null) {
            return List.of();
        }
        ArrayList<FieldMap> result = new ArrayList<>();
        for (T source : sources) {
            FieldMap mapped = this.map(source);
            if (mapped == null) {
                continue;
            }
            result.add(mapped);
        }
        return result;
    }

    public <T> T map(FieldMap fieldMap, Class<T> targetType) {
        if (fieldMap == null) {
            return null;
        }
        Map<String, Object> rawMap = FieldMapValueConverter.fieldMapToMap(fieldMap);
        Map<String, Object> mapped = this.mapByAnnotation(rawMap, targetType);
        return this.objectMapper.convertValue(mapped, targetType);
    }

    public <T> T mapFirst(DataResponse response, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return null;
        }
        return this.map(response.getRecords(0), targetType);
    }

    public <T> Optional<T> mapFirstOptional(DataResponse response, Class<T> targetType) {
        return Optional.ofNullable(this.mapFirst(response, targetType));
    }

    public <T> T mapFirst(Iterable<FieldMap> rows, Class<T> targetType) {
        if (rows == null) {
            return null;
        }
        Iterator<FieldMap> iterator = rows.iterator();
        if (iterator.hasNext()) {
            FieldMap row = iterator.next();
            return this.map(row, targetType);
        }
        return null;
    }

    public <T> Optional<T> mapFirstOptional(Iterable<FieldMap> rows, Class<T> targetType) {
        return Optional.ofNullable(this.mapFirst(rows, targetType));
    }

    public byte[] mapFirstBytes(DataResponse response) {
        return this.mapSingleValue(response, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(DataResponse response) {
        return Optional.ofNullable(this.mapFirstBytes(response));
    }

    public byte[] mapFirstBytes(DataResponse response, String fieldName) {
        return this.mapSingleValue(response, fieldName, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(DataResponse response, String fieldName) {
        return Optional.ofNullable(this.mapFirstBytes(response, fieldName));
    }

    public byte[] mapFirstBytes(FieldMap row) {
        return this.mapSingleValue(row, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(FieldMap row) {
        return Optional.ofNullable(this.mapFirstBytes(row));
    }

    public byte[] mapFirstBytes(FieldMap row, String fieldName) {
        return this.mapSingleValue(row, fieldName, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(FieldMap row, String fieldName) {
        return Optional.ofNullable(this.mapFirstBytes(row, fieldName));
    }

    public byte[] mapFirstBytes(Iterable<FieldMap> rows) {
        return this.mapSingleValue(rows, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(Iterable<FieldMap> rows) {
        return Optional.ofNullable(this.mapFirstBytes(rows));
    }

    public byte[] mapFirstBytes(Iterable<FieldMap> rows, String fieldName) {
        return this.mapSingleValue(rows, fieldName, byte[].class);
    }

    public Optional<byte[]> mapFirstBytesOptional(Iterable<FieldMap> rows, String fieldName) {
        return Optional.ofNullable(this.mapFirstBytes(rows, fieldName));
    }

    public <T> T mapSingleValue(DataResponse response, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return null;
        }
        return this.mapSingleValue(response.getRecords(0), targetType);
    }

    public <T> Optional<T> mapSingleValueOptional(DataResponse response, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(response, targetType));
    }

    public <T> T mapSingleValue(DataResponse response, String fieldName, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return null;
        }
        return this.mapSingleValue(response.getRecords(0), fieldName, targetType);
    }

    public <T> Optional<T> mapSingleValueOptional(DataResponse response, String fieldName, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(response, fieldName, targetType));
    }

    public <T> T mapSingleValue(FieldMap row, Class<T> targetType) {
        if (row == null) {
            return null;
        }
        Map<String, Object> rawMap = FieldMapValueConverter.fieldMapToMap(row);
        if (rawMap.isEmpty()) {
            return null;
        }
        Object value = rawMap.values().iterator().next();
        return this.convertValue(value, targetType);
    }

    public <T> Optional<T> mapSingleValueOptional(FieldMap row, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(row, targetType));
    }

    public <T> T mapSingleValue(FieldMap row, String fieldName, Class<T> targetType) {
        if (row == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Map<String, Object> rawMap = FieldMapValueConverter.fieldMapToMap(row);
        Object value = rawMap.get(fieldName);
        return this.convertValue(value, targetType);
    }

    public <T> Optional<T> mapSingleValueOptional(FieldMap row, String fieldName, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(row, fieldName, targetType));
    }

    public <T> T mapSingleValue(Iterable<FieldMap> rows, Class<T> targetType) {
        if (rows == null) {
            return null;
        }
        Iterator<FieldMap> iterator = rows.iterator();
        if (iterator.hasNext()) {
            FieldMap row = iterator.next();
            return this.mapSingleValue(row, targetType);
        }
        return null;
    }

    public <T> Optional<T> mapSingleValueOptional(Iterable<FieldMap> rows, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(rows, targetType));
    }

    public <T> T mapSingleValue(Iterable<FieldMap> rows, String fieldName, Class<T> targetType) {
        if (rows == null) {
            return null;
        }
        Iterator<FieldMap> iterator = rows.iterator();
        if (iterator.hasNext()) {
            FieldMap row = iterator.next();
            return this.mapSingleValue(row, fieldName, targetType);
        }
        return null;
    }

    public <T> Optional<T> mapSingleValueOptional(Iterable<FieldMap> rows, String fieldName, Class<T> targetType) {
        return Optional.ofNullable(this.mapSingleValue(rows, fieldName, targetType));
    }

    public <T> List<T> mapAll(Iterable<FieldMap> rows, Class<T> targetType) {
        if (rows == null) {
            return List.of();
        }
        ArrayList<T> result = new ArrayList<>();
        for (FieldMap row : rows) {
            T mapped = this.map(row, targetType);
            if (mapped == null) {
                continue;
            }
            result.add(mapped);
        }
        return result;
    }

    public <T> List<T> mapAll(DataResponse response, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return List.of();
        }
        return this.mapAll(response.getRecordsList(), targetType);
    }

    public <T> List<T> mapAllSingleValue(Iterable<FieldMap> rows, Class<T> targetType) {
        if (rows == null) {
            return List.of();
        }
        ArrayList<T> result = new ArrayList<>();
        for (FieldMap row : rows) {
            T mapped = this.mapSingleValue(row, targetType);
            if (mapped == null) {
                continue;
            }
            result.add(mapped);
        }
        return result;
    }

    public <T> List<T> mapAllSingleValue(Iterable<FieldMap> rows, String fieldName, Class<T> targetType) {
        if (rows == null) {
            return List.of();
        }
        ArrayList<T> result = new ArrayList<>();
        for (FieldMap row : rows) {
            T mapped = this.mapSingleValue(row, fieldName, targetType);
            if (mapped == null) {
                continue;
            }
            result.add(mapped);
        }
        return result;
    }

    public <T> List<T> mapAllSingleValue(DataResponse response, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return List.of();
        }
        return this.mapAllSingleValue(response.getRecordsList(), targetType);
    }

    public <T> List<T> mapAllSingleValue(DataResponse response, String fieldName, Class<T> targetType) {
        if (response == null || response.getRecordsCount() == 0) {
            return List.of();
        }
        return this.mapAllSingleValue(response.getRecordsList(), fieldName, targetType);
    }

    private Map<String, Object> normalizeMap(Map<?, ?> sourceMap) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private Map<String, Object> mapToDasFields(Map<String, Object> sourceMap, Class<?> sourceType) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Map<String, String> methodKeys = this.resolveMethodKeys(sourceType);
        for (Class<?> current = sourceType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !sourceMap.containsKey(field.getName())) {
                    continue;
                }
                String key = methodKeys.getOrDefault(field.getName(), this.resolveKey(field));
                if (key == null || key.isBlank()) {
                    key = field.getName();
                }
                result.put(key, sourceMap.get(field.getName()));
            }
        }
        if (result.isEmpty()) {
            result.putAll(sourceMap);
        }
        return result;
    }

    private Map<String, Object> mapByAnnotation(Map<String, Object> rawMap, Class<?> targetType) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Map<String, String> methodKeys = this.resolveMethodKeys(targetType);
        for (Class<?> current = targetType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String key = methodKeys.getOrDefault(field.getName(), this.resolveKey(field));
                if (key == null || key.isBlank()) {
                    key = field.getName();
                }
                if (!rawMap.containsKey(key)) {
                    continue;
                }
                result.put(field.getName(), rawMap.get(key));
            }
        }
        return result;
    }

    private String resolveKey(Field field) {
        DasField annotation = field.getAnnotation(DasField.class);
        if (annotation != null) {
            return annotation.value();
        }
        return null;
    }

    private Map<String, String> resolveMethodKeys(Class<?> targetType) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Class<?> current = targetType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                DasField annotation = method.getAnnotation(DasField.class);
                if (annotation == null || method.getParameterCount() != 0) {
                    continue;
                }
                String propertyName = this.methodPropertyName(method);
                if (propertyName == null || propertyName.isBlank()) {
                    continue;
                }
                result.putIfAbsent(propertyName, annotation.value());
            }
        }
        return result;
    }

    private String methodPropertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return this.decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
            return this.decapitalize(name.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        char lower = Character.toLowerCase(first);
        if (first == lower) {
            return value;
        }
        return lower + value.substring(1);
    }

    private <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        return this.objectMapper.convertValue(value, targetType);
    }
}
