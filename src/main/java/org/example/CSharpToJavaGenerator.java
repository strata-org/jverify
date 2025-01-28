package org.example;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class CSharpToJavaGenerator {
    private static final Map<String, String> TYPE_MAPPINGS = Map.of(
            "string", "String",
            "int", "int",
            "long", "long",
            "double", "double",
            "bool", "boolean",
            "DateTime", "Instant",
            "Dictionary", "Map",
            "List", "List"
    );

    static class ClassInfo {
        String name;
        List<ParameterInfo> parameters;
        String baseClass;
        boolean isGeneric;
        List<String> typeParameters;

        ClassInfo(String name, List<ParameterInfo> parameters, String baseClass) {
            this.name = name;
            this.parameters = parameters;
            this.baseClass = baseClass;
            this.typeParameters = new ArrayList<>();
        }
    }

    static class ParameterInfo {
        String type;
        String name;
        boolean isNullable;

        ParameterInfo(String type, String name, boolean isNullable) {
            this.type = type;
            this.name = name;
            this.isNullable = isNullable;
        }
    }

    static class GeneratedFile {
        String packageName;
        Set<String> imports;
        List<String> classes;

        GeneratedFile() {
            this.imports = new TreeSet<>();  // Using TreeSet for sorted imports
            this.classes = new ArrayList<>();
        }
    }

    public GeneratedFile generateFromFile(String content) {
        GeneratedFile result = new GeneratedFile();

        // Split the content into lines
        String[] lines = content.split("\n");
        StringBuilder currentRecord = new StringBuilder();
        boolean insideRecord = false;

        for (String line : lines) {
            line = line.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            // Check for package declaration
            if (line.startsWith("namespace")) {
                result.packageName = line.substring(9).trim().replace(";", "");
                continue;
            }

            // Handle record declarations
            if (line.startsWith("record") || insideRecord) {
                currentRecord.append(line).append(" ");

                // Check if we've reached the end of the record declaration
                if (line.endsWith(";") || line.endsWith("}")) {
                    String recordDecl = currentRecord.toString().trim();
                    if (recordDecl.endsWith(";")) {
                        recordDecl = recordDecl.substring(0, recordDecl.length() - 1);
                    }
                    try {
                        String javaClass = generateJavaClass(recordDecl);
                        result.classes.add(javaClass);

                        // Add required imports based on the record
                        addRequiredImports(recordDecl, result.imports);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Failed to parse record: " + recordDecl);
                        e.printStackTrace();
                    }

                    currentRecord = new StringBuilder();
                    insideRecord = false;
                } else {
                    insideRecord = true;
                }
            }

            // Handle enum declarations
            if (line.startsWith("enum")) {
                String enumDecl = line;
                if (!line.contains("{")) {
                    insideRecord = true;
                    continue;
                }
                if (!line.endsWith("}")) {
                    insideRecord = true;
                    continue;
                }
                result.classes.add(convertEnum(enumDecl));
            }
        }

        return result;
    }

    private void addRequiredImports(String recordDecl, Set<String> imports) {
        if (recordDecl.contains("DateTime")) {
            imports.add("java.time.Instant");
        }
        if (recordDecl.contains("?")) {
            imports.add("javax.annotation.Nullable");
        }
        if (recordDecl.contains("List<")) {
            imports.add("java.util.List");
        }
        if (recordDecl.contains("Dictionary<")) {
            imports.add("java.util.Map");
        }
        if (recordDecl.contains("Option<")) {
            imports.add("java.util.Optional");
        }
        // Add lombok imports
        imports.add("lombok.Data");
        imports.add("lombok.AllArgsConstructor");
        if (recordDecl.contains(":")) {
            imports.add("lombok.EqualsAndHashCode");
        }
    }

    private String convertEnum(String enumDecl) {
        return enumDecl.replace("enum", "public enum") + ";";
    }

    public String generateJavaClass(String csharpRecord) {
        ClassInfo classInfo = parseRecord(csharpRecord);
        return generateJavaCode(classInfo);
    }

    private ClassInfo parseRecord(String input) {
        Pattern pattern = Pattern.compile(
                "record\\s+(\\w+)(?:<([\\w,\\s]+)>)?\\s*" +  // name and optional generic parameters
                        "\\((.*?)\\)\\s*" +                          // parameters
                        "(?::\\s*(\\w+(?:<[\\w,\\s]+>)?))?"         // optional base class
        );

        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid record format: " + input);
        }

        String name = matcher.group(1);
        String generics = matcher.group(2);
        String parameters = matcher.group(3);
        String baseClass = matcher.group(4);

        List<ParameterInfo> paramList = parseParameters(parameters);
        ClassInfo classInfo = new ClassInfo(name, paramList, baseClass);

        if (generics != null) {
            classInfo.isGeneric = true;
            classInfo.typeParameters = Arrays.stream(generics.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }

        return classInfo;
    }

    private List<ParameterInfo> parseParameters(String parameters) {
        List<ParameterInfo> result = new ArrayList<>();
        if (parameters.trim().isEmpty()) {
            return result;
        }

        String[] parts = parameters.split(",(?![^<>]*>)");

        for (String part : parts) {
            part = part.trim();
            String[] typeAndName = part.split("\\s+");

            String type = typeAndName[0];
            String name = typeAndName[1];
            boolean isNullable = type.endsWith("?");

            if (isNullable) {
                type = type.substring(0, type.length() - 1);
            }

            type = convertType(type);
            name = toCamelCase(name);

            result.add(new ParameterInfo(type, name, isNullable));
        }

        return result;
    }

    private String convertType(String csharpType) {
        if (csharpType.contains("<")) {
            Pattern genericPattern = Pattern.compile("(\\w+)<(.+)>");
            Matcher matcher = genericPattern.matcher(csharpType);
            if (matcher.find()) {
                String baseType = TYPE_MAPPINGS.getOrDefault(matcher.group(1), matcher.group(1));
                String[] genericParams = matcher.group(2).split(",");
                String convertedParams = Arrays.stream(genericParams)
                        .map(String::trim)
                        .map(this::convertType)
                        .collect(Collectors.joining(", "));
                return baseType + "<" + convertedParams + ">";
            }
        }

        return TYPE_MAPPINGS.getOrDefault(csharpType, csharpType);
    }
// Previous imports and class structure remain the same,
// but removing lombok imports and modifying generateJavaCode

    private String generateJavaCode(ClassInfo classInfo) {
        StringBuilder java = new StringBuilder();

        // Generate class declaration
        java.append("public class ").append(classInfo.name);

        // Add generic parameters if any
        if (classInfo.isGeneric) {
            java.append("<")
                    .append(String.join(", ", classInfo.typeParameters))
                    .append(">");
        }

        // Add base class if exists
        if (classInfo.baseClass != null) {
            java.append(" extends ").append(convertType(classInfo.baseClass));
        }

        java.append(" {\n");

        // Generate fields
        for (ParameterInfo param : classInfo.parameters) {
            java.append("    ");
            if (param.isNullable) {
                java.append("@Nullable ");
            }
            java.append("private final ").append(param.type)
                    .append(" ").append(param.name).append(";\n");
        }

        java.append("\n");

        // Generate constructor
        java.append("    public ").append(classInfo.name);
        if (classInfo.isGeneric) {
            java.append("<")
                    .append(String.join(", ", classInfo.typeParameters))
                    .append(">");
        }
        java.append("(");

        // Constructor parameters
        java.append(classInfo.parameters.stream()
                .map(p -> {
                    String paramStr = "";
                    if (p.isNullable) {
                        paramStr += "@Nullable ";
                    }
                    paramStr += p.type + " " + p.name;
                    return paramStr;
                })
                .collect(Collectors.joining(", ")));

        java.append(") {\n");

        // Call super constructor if there's a base class
        if (classInfo.baseClass != null) {
            java.append("        super(")
                    .append(classInfo.parameters.stream()
                            .limit(3) // This should match base class parameters
                            .map(p -> p.name)
                            .collect(Collectors.joining(", ")))
                    .append(");\n");
        }

        // Initialize fields
        for (ParameterInfo param : classInfo.parameters) {
            java.append("        this.").append(param.name)
                    .append(" = ").append(param.name).append(";\n");
        }

        java.append("    }\n\n");

        // Generate getters
        for (ParameterInfo param : classInfo.parameters) {
            java.append("    public ")
                    .append(param.type)
                    .append(" get")
                    .append(Character.toUpperCase(param.name.charAt(0)))
                    .append(param.name.substring(1))
                    .append("() {\n")
                    .append("        return this.")
                    .append(param.name)
                    .append(";\n    }\n\n");
        }

        // Generate equals method
        java.append("    @Override\n")
                .append("    public boolean equals(Object o) {\n")
                .append("        if (this == o) return true;\n")
                .append("        if (o == null || getClass() != o.getClass()) return false;\n");

        if (classInfo.baseClass != null) {
            java.append("        if (!super.equals(o)) return false;\n");
        }

        java.append("        ").append(classInfo.name);
        if (classInfo.isGeneric) {
            java.append("<?>");
        }
        java.append(" that = (").append(classInfo.name);
        if (classInfo.isGeneric) {
            java.append("<?>");
        }
        java.append(") o;\n");

        // Field comparisons
        for (ParameterInfo param : classInfo.parameters) {
            if (param.type.equals("float")) {
                java.append("        if (Float.compare(that.")
                        .append(param.name).append(", ")
                        .append(param.name).append(") != 0) return false;\n");
            } else if (param.type.equals("double")) {
                java.append("        if (Double.compare(that.")
                        .append(param.name).append(", ")
                        .append(param.name).append(") != 0) return false;\n");
            } else if (isJavaPrimitive(param.type)) {
                java.append("        if (").append(param.name)
                        .append(" != that.").append(param.name)
                        .append(") return false;\n");
            } else {
                java.append("        if (").append(param.name)
                        .append(" != null ? !").append(param.name)
                        .append(".equals(that.").append(param.name)
                        .append(") : that.").append(param.name)
                        .append(" != null) return false;\n");
            }
        }

        java.append("        return true;\n    }\n\n");

        // Generate hashCode method
        java.append("    @Override\n")
                .append("    public int hashCode() {\n")
                .append("        int result = ");

        if (classInfo.baseClass != null) {
            java.append("super.hashCode();\n");
        } else {
            if (!classInfo.parameters.isEmpty()) {
                ParameterInfo firstParam = classInfo.parameters.get(0);
                if (isJavaPrimitive(firstParam.type)) {
                    if (firstParam.type.equals("long")) {
                        java.append("(int) (").append(firstParam.name).append(" ^ (").append(firstParam.name).append(" >>> 32));\n");
                    } else if (firstParam.type.equals("float")) {
                        java.append("Float.floatToIntBits(").append(firstParam.name).append(");\n");
                    } else if (firstParam.type.equals("double")) {
                        java.append("(int) (Double.doubleToLongBits(").append(firstParam.name).append(") ^ (Double.doubleToLongBits(").append(firstParam.name).append(") >>> 32));\n");
                    } else {
                        java.append(firstParam.name).append(";\n");
                    }
                } else {
                    java.append(firstParam.name).append(" != null ? ").append(firstParam.name).append(".hashCode() : 0;\n");
                }
            } else {
                java.append("0;\n");
            }
        }

        // Rest of the fields
        for (int i = classInfo.baseClass != null ? 0 : 1; i < classInfo.parameters.size(); i++) {
            ParameterInfo param = classInfo.parameters.get(i);
            java.append("        result = 31 * result + ");
            if (isJavaPrimitive(param.type)) {
                if (param.type.equals("long")) {
                    java.append("(int) (").append(param.name).append(" ^ (").append(param.name).append(" >>> 32))");
                } else if (param.type.equals("float")) {
                    java.append("Float.floatToIntBits(").append(param.name).append(")");
                } else if (param.type.equals("double")) {
                    java.append("(int) (Double.doubleToLongBits(").append(param.name).append(") ^ (Double.doubleToLongBits(").append(param.name).append(") >>> 32))");
                } else {
                    java.append(param.name);
                }
            } else {
                java.append("(").append(param.name).append(" != null ? ").append(param.name).append(".hashCode() : 0)");
            }
            java.append(";\n");
        }

        java.append("        return result;\n    }\n\n");

        // Generate toString method
        java.append("    @Override\n")
                .append("    public String toString() {\n")
                .append("        return \"").append(classInfo.name).append("{\" +\n");

        if (classInfo.baseClass != null) {
            java.append("            \"super=\" + super.toString() + \", \" +\n");
        }

        for (int i = 0; i < classInfo.parameters.size(); i++) {
            ParameterInfo param = classInfo.parameters.get(i);
            java.append("            \"").append(param.name).append("=\" + ")
                    .append(param.name);
            if (i < classInfo.parameters.size() - 1) {
                java.append(" + \", \" +\n");
            } else {
                java.append(" + \"}\";\n");
            }
        }

        java.append("    }\n");

        java.append("}\n");

        return java.toString();
    }

    private boolean isJavaPrimitive(String type) {
        return Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char")
                .contains(type.toLowerCase());
    }

    private String toCamelCase(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public String generateJavaFile(GeneratedFile file) {
        StringBuilder javaFile = new StringBuilder();

        if (file.packageName != null && !file.packageName.isEmpty()) {
            javaFile.append("package ").append(file.packageName).append(";\n\n");
        }

        if (!file.imports.isEmpty()) {
            for (String imp : file.imports) {
                javaFile.append("import ").append(imp).append(";\n");
            }
            javaFile.append("\n");
        }

        javaFile.append(String.join("\n", file.classes));

        return javaFile.toString();
    }
}