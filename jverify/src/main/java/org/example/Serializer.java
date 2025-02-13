package org.example;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.*;
import java.util.*;

public class Serializer {
    Encoder encoder;
    private static Map<String, String> simpleTypeNameMapping = Map.of();

    public Serializer(Encoder encoder) {
        this.encoder = encoder;
    }

    public <T> void serialize(Object obj) {
        serializeObject(obj);
    }

    private void serializeValue(Object value, AnnotatedType annotatedType) {
        var isNullable = annotatedType.getAnnotation(Nullable.class) != null;
        if (isNullable) {
            encoder.writeNullable(value == null);
            if (value == null) {
                return;
            }
        }
        if (value == null) {
            throw new RuntimeException();
        }

        var type = annotatedType.getType();
        Class<?> expectedClass;
        if (type instanceof Class<?> clazz) {
            expectedClass = clazz;
        } else if (type instanceof ParameterizedType parameterizedType) {
            expectedClass = (Class<?>) parameterizedType.getRawType();
        } else {
            throw new RuntimeException();
        }
            
        if (expectedClass.isEnum()) {
            encoder.writeInt(((Enum<?>) value).ordinal());
            return;
        }

        if (expectedClass.isArray()) {
            serializeArray(value, (AnnotatedParameterizedType) annotatedType);
            return;
        } else if (value instanceof List<?> list){
            serializeList(list, (AnnotatedParameterizedType) annotatedType);
            return;
        }

        boolean isAbstract = Object.class == expectedClass || (Object.class.isAssignableFrom(expectedClass) && Modifier.isAbstract(expectedClass.getModifiers()));
        if (isAbstract) {
            Class<?> actualType = value.getClass();
            encoder.writeQualifiedName(actualType.getSimpleName());
        }
        
        if (value instanceof String s) {
            encoder.writeString(s);
        } else if (value instanceof Map<?, ?>) {
            serializeMap((Map<?, ?>) value, (AnnotatedParameterizedType) annotatedType);
        } else if (value instanceof Integer i) {
            encoder.writeInt(i);
        } else if (value instanceof Boolean b){
            encoder.writeBool(b);
        } else {
            serializeObject(value);
        }
    }

    private void serializeList(List<?> list, AnnotatedParameterizedType arrayType) {
        int length = list.size();
        encoder.writeInt(length);
        var elementType = arrayType.getAnnotatedActualTypeArguments()[0];
        for (int i = 0; i < length; i++) {
            serializeValue(list.get(i), elementType);
        }
    }
    
    private void serializeArray(Object array, AnnotatedParameterizedType arrayType) {
        int length = Array.getLength(array);
        encoder.writeInt(length);
        var elementType = arrayType.getAnnotatedActualTypeArguments()[0];
        for (int i = 0; i < length; i++) {
            serializeValue(Array.get(array, i), elementType);
        }
    }

    private void serializeMap(Map<?, ?> map, AnnotatedParameterizedType mapType) {
        encoder.writeInt(map.size());
        var keyType = mapType.getAnnotatedActualTypeArguments()[0];
        var valueType = mapType.getAnnotatedActualTypeArguments()[1];
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            serializeValue(entry.getKey(), keyType);
            serializeValue(entry.getValue(), valueType);
        }
    }

    private void serializeObject(Object obj) {
        Class<?> clazz = obj.getClass();
        List<Field> fields = getSerializableFields(clazz);

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                serializeValue(value, field.getAnnotatedType());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to serialize field: " + field.getName(), e);
            }
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
}

