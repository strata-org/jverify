package org.example;

import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class CSharpToJavaConverter {
    // Simple class to hold C# class information
    static class CSharpClass {
        String className;
        String parentClass;
        List<CSharpField> fields;

        public CSharpClass(String className, String parentClass) {
            this.className = className;
            this.parentClass = parentClass;
            this.fields = new ArrayList<>();
        }
    }

    // In CSharpToJavaConverter class:

    static class CSharpField {
        String type;
        String name;
        boolean isGeneric;
        List<String> genericTypes;

        public CSharpField(String type, String name, boolean isGeneric, List<String> genericTypes) {
            this.type = type;
            this.name = name;
            this.isGeneric = isGeneric;
            this.genericTypes = genericTypes;
        }
    }

    public static List<CSharpClass> parseCSharpClasses(String csharpCode) {
        List<CSharpClass> classes = new ArrayList<>();

        // Pattern for class declaration with body: "public class ClassName : ParentClass { ... }"
        Pattern classWithBodyPattern = Pattern.compile(
                "class\\s+(\\w+)\\s*(?::\\s*(\\w+))?\\s*\\{([^}]*)}",
                Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher classWithBodyMatcher = classWithBodyPattern.matcher(csharpCode);

        while (classWithBodyMatcher.find()) {
            String className = classWithBodyMatcher.group(1);
            String parentClass = classWithBodyMatcher.group(2); // May be null
            String classBody = classWithBodyMatcher.group(3);

            CSharpClass csharpClass = new CSharpClass(className, parentClass);

            // Updated pattern for fields including generic types: "List<Type> fieldName;" or "Type fieldName;"
            Pattern fieldPattern = Pattern.compile(
                    "\\s+(\\w+)(?:<([\\w,\\s]+)>)?\\s+(\\w+)\\s*;"
            );
            Matcher fieldMatcher = fieldPattern.matcher(classBody);

            while (fieldMatcher.find()) {
                String baseType = fieldMatcher.group(1);
                String genericTypesStr = fieldMatcher.group(2);
                String fieldName = fieldMatcher.group(3);

                boolean isGeneric = genericTypesStr != null;
                List<String> genericTypes = new ArrayList<>();
                if (isGeneric) {
                    genericTypes = Arrays.stream(genericTypesStr.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList());
                }

                csharpClass.fields.add(new CSharpField(baseType, fieldName, isGeneric, genericTypes));
            }

            classes.add(csharpClass);
        }

        if (classes.isEmpty()) {
            throw new IllegalArgumentException("No valid C# class declarations found");
        }

        return classes;
    }

    private static TypeName convertCSharpTypeToJavaType(CSharpField field) {
        String baseType = convertCSharpTypeToJava(field.type);

        if (!field.isGeneric) {
            return ClassName.get("", baseType);
        }

        // Handle generic types
        ClassName rawType = ClassName.get("java.util", baseType);
        List<TypeName> typeArguments = field.genericTypes.stream()
                .map(type -> ClassName.get("", convertCSharpTypeToJava(type)))
                .collect(Collectors.toList());

        return ParameterizedTypeName.get(rawType, typeArguments.toArray(new TypeName[0]));
    }

    public static JavaFile generateJavaClass(CSharpClass csharpClass) {
        // Start building the class
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(csharpClass.className)
                .addModifiers(Modifier.PUBLIC);

        // Add parent class if exists
        if (csharpClass.parentClass != null) {
            classBuilder.superclass(ClassName.get("", csharpClass.parentClass));
        }

        // Add fields
        for (CSharpField field : csharpClass.fields) {
            int iteration = 0;
            for(;iteration < 5;iteration++) {
                var newName = field.name + (iteration == 0 ? "" : iteration);
                try {
                    // Use the new type conversion method
                    TypeName fieldType = convertCSharpTypeToJavaType(field);

                    FieldSpec fieldSpec = FieldSpec.builder(
                                    fieldType,
                                    newName,
                                    Modifier.PRIVATE)
                            .build();
                    classBuilder.addField(fieldSpec);

                    // Add getter with generic type
                    MethodSpec getter = MethodSpec.methodBuilder("get" + capitalize(newName))
                            .addModifiers(Modifier.PUBLIC)
                            .returns(fieldType)
                            .addStatement("return this.$N", newName)
                            .build();
                    classBuilder.addMethod(getter);

                    // Add setter with generic type
                    MethodSpec setter = MethodSpec.methodBuilder("set" + capitalize(newName))
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(fieldType, newName)
                            .addStatement("this.$N = $N", newName, newName)
                            .build();
                    classBuilder.addMethod(setter);
                    break;
                } catch(IllegalArgumentException _) {
                }
            }
            if (iteration == 5) {
                throw new RuntimeException("failed");
            }
        }

        // Build the Java file
        return JavaFile.builder("", classBuilder.build())
                .addFileComment("Generated from C# class")
                .build();
    }

    private static String convertCSharpTypeToJava(String csharpType) {
        // Add more type conversions as needed
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("string", "String");
        typeMap.put("int", "int");
        typeMap.put("bool", "boolean");
        typeMap.put("double", "double");
        typeMap.put("List", "List");  // Keep List as is
        typeMap.put("ModuleDefinition", "ModuleDefinition");  // Preserve custom types
        typeMap.put("TopLevelDecl", "TopLevelDecl");  // Add custom type

        return typeMap.getOrDefault(csharpType, csharpType);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static void writeJava(String cSharpCode, Appendable out) throws IOException {

        // Parse C# class
        List<CSharpClass> csharpClasses = parseCSharpClasses(cSharpCode);

        for (CSharpClass csharpClass : csharpClasses) {
            JavaFile javaFile = generateJavaClass(csharpClass);
            out.append("\n// Generated ").append(csharpClass.className).append(".java:\n");
            javaFile.writeTo(out);
        }
    }
}