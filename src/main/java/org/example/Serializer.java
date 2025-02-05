package org.example;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class Serializer {
    private static final char ARRAY_END = ']';
    private static final char SEPARATOR = ',';
    private static Map<String, String> simpleTypeNameMapping = Map.of();

    public static String serialize(Object obj) {
        if (obj == null) return "null";
        StringBuilder sb = new StringBuilder();
        serializeValue(obj, obj.getClass(), sb);
        return sb.toString();
    }

    private static void serializeValue(Object value, Class<?> expectedType, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
            return;
        }

        Class<?> actualType = value.getClass();

        if (actualType.isEnum()) {
            // For enums, just output the ordinal
            sb.append(((Enum<?>) value).ordinal());
            return;
        }


        if (actualType.isArray()) {
            serializeArray(value, sb);
        } else if (value instanceof String) {
            sb.append(escapeString((String) value));
        } else if (value instanceof Collection<?>) {
            serializeCollection((Collection<?>) value, sb);
        } else if (value instanceof Map<?, ?>) {
            serializeMap((Map<?, ?>) value, sb);
        } else if (isSimpleType(actualType)) {
            if (!expectedType.equals(actualType) && !expectedType.isPrimitive()) {
                var simpleName = actualType.getSimpleName();
                if (simpleTypeNameMapping.containsKey(simpleName)) {
                    simpleName = simpleTypeNameMapping.get(simpleName);
                }
                sb.append("+").append(simpleName).append(":");
            }
            sb.append(value.toString());
        } else {
            // Add '+' prefix when actual type differs from expected
            if (!expectedType.equals(actualType) && !expectedType.isPrimitive()) {
                sb.append("+").append(actualType.getSimpleName()).append(":");
            }

            serializeObject(value, sb);
        }
    }

    private static void serializeArray(Object array, StringBuilder sb) {
        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(SEPARATOR);
            serializeValue(Array.get(array, i), componentType, sb);
        }
        sb.append(ARRAY_END);
    }

    private static void serializeCollection(Collection<?> collection, StringBuilder sb) {
        boolean first = true;
        for (Object item : collection) {
            if (!first) sb.append(SEPARATOR);
            serializeValue(item, Object.class, sb);
            first = false;
        }
        sb.append(ARRAY_END);
    }

    private static void serializeMap(Map<?, ?> map, StringBuilder sb) {
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(SEPARATOR);
            serializeValue(entry.getKey(), Object.class, sb);
            sb.append(SEPARATOR);
            serializeValue(entry.getValue(), Object.class, sb);
            first = false;
        }
        sb.append(ARRAY_END);
    }

    private static void serializeObject(Object obj, StringBuilder sb) {
        Class<?> clazz = obj.getClass();
        List<Field> fields = getSerializableFields(clazz);

        boolean first = true;
        for (Field field : fields) {
            if (!first) sb.append(SEPARATOR);
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                serializeValue(value, field.getType(), sb);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to serialize field: " + field.getName(), e);
            }
            first = false;
        }
    }

    private static List<Field> getSerializableFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(0, Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private static String escapeString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Character.class.equals(type);
    }
}

