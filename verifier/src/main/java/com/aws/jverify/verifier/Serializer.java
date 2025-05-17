package com.aws.jverify.verifier;

import com.aws.jverify.generated.Expression;
import com.aws.jverify.generated.FrameExpression;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.*;
import java.util.*;

public class Serializer {
    Encoder encoder;
    private static Map<String, String> simpleTypeNameMapping = 
            Map.of("Integer", "Int32",
                    "Long", "Int64",
                    "Short", "Int16",
                    "Float", "BigDec",
                    "Double", "BigDec",
                    "Character", "Char");

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
            throw new RuntimeException("Cannot serialize null value if no nullable type is expected. Type was " + annotatedType);
        }

        var type = annotatedType.getType();
        Class<?> expectedClass;
        if (type instanceof Class<?> clazz) {
            expectedClass = clazz;
        } else if (type instanceof ParameterizedType parameterizedType) {
            expectedClass = (Class<?>) parameterizedType.getRawType();
        } else if (type instanceof TypeVariable<?> && value instanceof FrameExpression) {
            // cheat a little bit for `MethodOrFunction.reads`
            expectedClass = FrameExpression.class;
        } else if (type instanceof TypeVariable<?> && value instanceof Expression) {
            // cheat a little bit for `MethodOrFunction.decreases`
            expectedClass = Expression.class;
        } else {
            throw new RuntimeException("No support for serializing " + type.getClass().getName());
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
            String simpleName = actualType.getSimpleName();
            if (simpleTypeNameMapping.containsKey(simpleName)) {
                simpleName = simpleTypeNameMapping.get(simpleName);
            }
            encoder.writeQualifiedName(simpleName);
        }

        if (value instanceof String) {
            encoder.writeString((String) value);
        } else if (value instanceof Map) {
            serializeMap((Map<?, ?>) value, (AnnotatedParameterizedType) annotatedType);
        } else if (value instanceof Integer) {
            encoder.writeInt((Integer) value);
        } else if (value instanceof Long) {
            encoder.writeLong((Long) value);
        } else if (value instanceof Float) {
            encoder.writeDouble((double) ((Float) value));
        } else if (value instanceof Double) {
            encoder.writeDouble((Double) value);
        } else if (value instanceof Boolean) {
            encoder.writeBool((Boolean) value);
        } else if (value instanceof Character) {
            encoder.writeString(new String(new char[] {(Character) value}));
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
        if (!clazz.getPackageName().contains("generated")) {
            throw new RuntimeException("serializeObject should not be called on value of type " + clazz);
        }
        List<Field> fields = getSerializableFields(clazz);

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                serializeValue(value, field.getAnnotatedType());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to serialize field: " + field.getName(), e);
            } catch (InaccessibleObjectException e) {
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

