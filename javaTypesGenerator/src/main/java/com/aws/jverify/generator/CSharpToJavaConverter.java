package com.aws.jverify.generator;

import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CSharpToJavaConverter {
    Set<String> foundClasses = new HashSet<>();

    public CSharpToJavaConverter(String packageName) {
        this.packageName = packageName;
    }

    String packageName;

    static class CSharpEnum {
        String enumName;
        List<String> values;

        public CSharpEnum(String enumName) {
            this.enumName = enumName;
            this.values = new ArrayList<>();
        }
    }

    // Add this method to parse enums
    public static List<CSharpEnum> parseCSharpEnums(String csharpCode) {
        List<CSharpEnum> enums = new ArrayList<>();

        // Pattern for enum declaration: "enum EnumName { Value1, Value2, Value3 }"
        Pattern enumPattern = Pattern.compile(
                "enum\\s+(\\w+)\\s*\\{([^}]*)\\}",
                Pattern.MULTILINE
        );
        Matcher enumMatcher = enumPattern.matcher(csharpCode);

        while (enumMatcher.find()) {
            String enumName = enumMatcher.group(1);
            String enumBody = enumMatcher.group(2);

            CSharpEnum csharpEnum = new CSharpEnum(enumName);

            // Split enum values and trim whitespace
            String[] values = enumBody.split(",");
            for (String value : values) {
                String trimmedValue = value.trim();
                if (!trimmedValue.isEmpty()) {
                    csharpEnum.values.add(trimmedValue);
                }
            }

            enums.add(csharpEnum);
        }

        return enums;
    }

    // Add this method to generate Java enum
    public static JavaFile generateJavaEnum(CSharpEnum csharpEnum) {
        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(csharpEnum.enumName)
                .addModifiers(Modifier.PUBLIC);

        // Add enum constants
        for (String value : csharpEnum.values) {
            enumBuilder.addEnumConstant(value);
        }

        return JavaFile.builder("", enumBuilder.build())
                .addFileComment("Generated from C# enum")
                .build();
    }

    // Simple class to hold C# class information
    static class CSharpClass {
        String className;
        String parentClass;
        List<CSharpField> fields;
        boolean isAbstract;
        CSharpClass parentClassRef;
        List<TypeParameter> typeParameters;  // Generic type parameters

        public CSharpClass(boolean isAbstract, String className, String parentClass) {
            this.isAbstract = isAbstract;
            this.className = className;
            this.parentClass = parentClass;
            this.fields = new ArrayList<>();
            this.parentClassRef = null;
            this.typeParameters = new ArrayList<>();
        }

        public List<CSharpField> getAllFields() {
            List<CSharpField> allFields = new ArrayList<>();
            if (parentClassRef != null) {
                allFields.addAll(parentClassRef.getAllFields());
            }
            allFields.addAll(fields);
            return allFields;
        }
    }

    static class TypeParameter {
        String name;
        String constraint;  // The type constraint (e.g., "Node" in "T : Node")

        public TypeParameter(String name, String constraint) {
            this.name = name;
            this.constraint = constraint;
        }
    }

    static class CSharpField {
        boolean isNullable;
        String type;
        String name;
        boolean isGeneric;
        List<String> genericTypes;

        public CSharpField(String type, String name, boolean isNullable, boolean isGeneric, List<String> genericTypes) {
            this.type = type;
            this.name = name;
            this.isNullable = isNullable;
            this.isGeneric = isGeneric;
            this.genericTypes = genericTypes;
        }
    }

    public List<CSharpClass> parseCSharpClasses(String csharpCode) {
        List<CSharpClass> classes = new ArrayList<>();
        Map<String, CSharpClass> classMap = new HashMap<>();

        // Updated pattern for class declaration with generics and constraints
        // Example: "class Specification<T> : NodeWithComputedRange where T : Node"
        Pattern classWithBodyPattern = Pattern.compile(
                "(?<abstract>abstract)?\\s*" +
                        "class\\s+" +
                        "(?<name>\\w+)\\s*" +
                        "(?:<(?<typeParams>[^>]+)>)?\\s*" +
                        "(?::\\s*(?<parentClass>\\w+))?\\s*" +
                        "(?:where\\s+(?<constraints>[^{]+))?\\s*" +
                        "\\{(?<body>[^}]*)}",
                Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher classWithBodyMatcher = classWithBodyPattern.matcher(csharpCode);

        while (classWithBodyMatcher.find()) {
            String abstractKeyword = classWithBodyMatcher.group("abstract");
            String className = classWithBodyMatcher.group("name");
            String typeParams = classWithBodyMatcher.group("typeParams");  // Type parameters (e.g., "T")
            String parentClass = classWithBodyMatcher.group("parentClass"); // Parent class
            String constraints = classWithBodyMatcher.group("constraints"); // Type constraints
            String classBody = classWithBodyMatcher.group("body");

            CSharpClass csharpClass = new CSharpClass(abstractKeyword != null, className, parentClass);

            // Parse type parameters and constraints
            if (typeParams != null) {
                String[] params = typeParams.split(",");
                for (String param : params) {
                    String paramName = param.trim();
                    String constraint = null;

                    // Find matching constraint if it exists
                    if (constraints != null) {
                        Pattern constraintPattern = Pattern.compile(
                                paramName + "\\s*:\\s*(\\w+)"
                        );
                        Matcher constraintMatcher = constraintPattern.matcher(constraints);
                        if (constraintMatcher.find()) {
                            constraint = constraintMatcher.group(1);
                        }
                    }

                    csharpClass.typeParameters.add(new TypeParameter(paramName, constraint));
                }
            }

            // Parse fields
            Pattern fieldPattern = Pattern.compile(
                    "\\s+(\\w+)(?:<([\\w,\\s]+)>)?(\\?)?\\s+(\\w+)\\s*;"
            );
            Matcher fieldMatcher = fieldPattern.matcher(classBody);

            while (fieldMatcher.find()) {
                String baseType = fieldMatcher.group(1);
                String genericTypesStr = fieldMatcher.group(2);
                String questionMark = fieldMatcher.group(3);
                String fieldName = fieldMatcher.group(4);

                boolean isGeneric = genericTypesStr != null;
                List<String> genericTypes = new ArrayList<>();
                if (isGeneric) {
                    genericTypes = Arrays.stream(genericTypesStr.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList());
                }
                var isNullable = questionMark != null;

                csharpClass.fields.add(new CSharpField(baseType, fieldName, isNullable, isGeneric, genericTypes));
            }

            classes.add(csharpClass);
            foundClasses.add(csharpClass.className);
            classMap.put(className, csharpClass);
        }

        // Link parent classes
        for (CSharpClass csharpClass : classes) {
            if (csharpClass.parentClass != null) {
                csharpClass.parentClassRef = classMap.get(csharpClass.parentClass);
            }
        }

        return classes;
    }

    private TypeName convertCSharpFieldToJavaFieldType(CSharpField field, List<TypeParameter> classTypeParameters) {
        // Check if the type is a type parameter
        Optional<TypeParameter> typeParam = classTypeParameters.stream()
                .filter(tp -> tp.name.equals(field.type))
                .findFirst();

        if (typeParam.isPresent()) {
            // If it's a type parameter with a constraint, return the bounded type
            if (typeParam.get().constraint != null) {
                return WildcardTypeName.subtypeOf(ClassName.get("", typeParam.get().constraint));
            }
            // If it's an unbounded type parameter, return it as is
            return TypeVariableName.get(field.type);
        }

        String baseType = convertCSharpTypeToJava(field.type);

        if (!field.isGeneric) {
            return ClassName.get("", baseType);
        }

        // Handle generic types
        ClassName rawType = foundClasses.contains(baseType)
                ? ClassName.get(packageName, baseType)
                : ClassName.get("java.util", baseType);
        List<TypeName> typeArguments = field.genericTypes.stream()
                .map(type -> {
                    // Check if the generic argument is a type parameter
                    Optional<TypeParameter> genericTypeParam = classTypeParameters.stream()
                            .filter(tp -> tp.name.equals(type))
                            .findFirst();

                    if (genericTypeParam.isPresent()) {
                        return TypeVariableName.get(type);
                    }
                    return ClassName.get("", convertCSharpTypeToJava(type));
                })
                .collect(Collectors.toList());

        return ParameterizedTypeName.get(rawType, typeArguments.toArray(new TypeName[0]));
    }

    public JavaFile generateJavaClass(CSharpClass csharpClass) {
        // Start building the class with type parameters
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(csharpClass.className)
                .addModifiers(Modifier.PUBLIC);

        // Add type parameters with bounds
        for (TypeParameter typeParam : csharpClass.typeParameters) {
            TypeVariableName typeVariable = TypeVariableName.get(
                    typeParam.name,
                    typeParam.constraint != null
                            ? ClassName.get("", typeParam.constraint)
                            : TypeName.OBJECT
            );
            classBuilder.addTypeVariable(typeVariable);
        }
        
        if (csharpClass.isAbstract) {
            classBuilder.addModifiers(Modifier.ABSTRACT);
        }
            
        // Add parent class if exists
        if (csharpClass.parentClass != null) {
            classBuilder.superclass(ClassName.get("", csharpClass.parentClass));
        }

        // Prepare constructor
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        // Get all fields including inherited ones
        List<CSharpField> allFields = csharpClass.getAllFields();

        // Add fields and prepare constructor parameters
        for (CSharpField field : allFields) {
            int iteration = 0;
            // Try several names in case some collide with reserved names
            for(;iteration < 5;iteration++) {
                var newName = field.name + (iteration == 0 ? "" : iteration);
                try {
                    TypeName fieldType = convertCSharpFieldToJavaFieldType(field, csharpClass.typeParameters);

                    // Only add field if it's from this class (not inherited)
                    if (csharpClass.fields.contains(field)) {
                        var builder = FieldSpec.builder(
                                fieldType,
                                newName,
                                Modifier.PRIVATE, Modifier.FINAL);
                        if (field.isNullable) {
                            builder.addAnnotation(Nullable.class);
                        }
                        FieldSpec fieldSpec = builder.build();
                        classBuilder.addField(fieldSpec);
                    }

                    // Add parameter to constructor
                    constructorBuilder.addParameter(fieldType, newName);

                    // Add getter only for fields from this class
                    if (csharpClass.fields.contains(field)) {
                        MethodSpec getter = MethodSpec.methodBuilder("get" + capitalize(newName))
                                .addModifiers(Modifier.PUBLIC)
                                .returns(fieldType)
                                .addStatement("return this.$N", newName)
                                .build();
                        classBuilder.addMethod(getter);
                    }
                    break;
                } catch(IllegalArgumentException ignored) {
                }
            }
            if (iteration == 5) {
                throw new RuntimeException("could not find a valid name for field " + field.name + " after 5 attempts");
            }
        }

        // Add super() call if there's a parent class
        if (csharpClass.parentClassRef != null) {
            List<CSharpField> parentFields = csharpClass.parentClassRef.getAllFields();
            StringBuilder superCall = new StringBuilder("super(");
            for (int i = 0; i < parentFields.size(); i++) {
                if (i > 0) superCall.append(", ");
                var safeName = protectName(parentFields.get(i).name);
                superCall.append(safeName);
            }
            superCall.append(")");
            constructorBuilder.addStatement(superCall.toString());
        }

        // Add field initializations
        for (CSharpField field : csharpClass.fields) {
            String safeName = protectName(field.name);
            constructorBuilder.addStatement("this.$N = $N", safeName, safeName);
        }

        // Add constructor to class
        classBuilder.addMethod(constructorBuilder.build());

        // Build the Java file
        return JavaFile.builder("", classBuilder.build())
                .addFileComment("Generated from C# class")
                .build();
    }

    static Set<String> keywords = Set.of("implements", "extends");
    private static String protectName(String name) {
        if (keywords.contains(name)) {
            return name + "1";
        } else {
            return name;
        }
    }

    private static String convertCSharpTypeToJava(String csharpType) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("string", "String");
        typeMap.put("int", "int");
        typeMap.put("bool", "boolean");
        typeMap.put("Int32", "int");
        typeMap.put("double", "double");
        typeMap.put("List", "List");
        typeMap.put("ModuleDefinition", "ModuleDefinition");
        typeMap.put("TopLevelDecl", "TopLevelDecl");
        typeMap.put("Node", "Node");
        typeMap.put("NodeWithComputedRange", "NodeWithComputedRange");
        typeMap.put("Attributes", "Attributes");

        return typeMap.getOrDefault(csharpType, csharpType);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public void writeJava(String cSharpCode, Path outputDirectory) throws IOException {

        // Parse C# class
        List<CSharpClass> csharpClasses = parseCSharpClasses(cSharpCode);
        List<CSharpEnum> csharpEnums = parseCSharpEnums(cSharpCode);
        for (CSharpEnum cSharpEnum : csharpEnums) {
            JavaFile javaFile = generateJavaEnum(cSharpEnum);
            Path d = outputDirectory.resolve(cSharpEnum.enumName + ".java");
            var filePath = Files.newBufferedWriter(d);
            filePath.append(String.format("package %s;\n", packageName));
            filePath.append("\n// Generated ").append(cSharpEnum.enumName).append(".java:\n");
            javaFile.writeTo(filePath);
            filePath.close();
        }
        for (CSharpClass csharpClass : csharpClasses) {
            JavaFile javaFile = generateJavaClass(csharpClass);
            Path d = outputDirectory.resolve(csharpClass.className + ".java");
            var filePath = Files.newBufferedWriter(d);
            filePath.append(String.format("package %s;\n", packageName));
            filePath.append("\n// Generated ").append(csharpClass.className).append(".java:\n");
            javaFile.writeTo(filePath);
            filePath.close();
        }
    }
}