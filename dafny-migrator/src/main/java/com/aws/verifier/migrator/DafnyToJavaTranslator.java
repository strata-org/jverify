package com.aws.verifier.migrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * DafnyToJavaTranslator
 *
 * Translates Dafny files to Java files with JVerify specifications and proofs.
 */
public class DafnyToJavaTranslator {
    private static final String JVERIFY_IMPORT = "import static com.aws.jverify.JVerify.*;";
    private static final String PURE_IMPORT = "import com.aws.jverify.Pure;";
    private static final String ERASED_IMPORT = "import com.aws.jverify.Erased;";
    private static final String UNBOUNDED_IMPORT = "import com.aws.jverify.Unbounded;";
    private static final String NAT_IMPORT = "import com.aws.jverify.Nat;";
    private static final String NULLABLE_IMPORT = "import com.aws.jverify.Nullable;";
    private static final String PROOF_IMPORT = "import com.aws.jverify.Proof;";
    private static final String INVARIANT_IMPORT = "import com.aws.jverify.Invariant;";

    private String packageName;
    private final List<String> imports = new ArrayList<>();
    private final List<String> methods = new ArrayList<>();
    private final List<String> classes = new ArrayList<>();
    private StringBuilder currentClassBuilder;
    private String className;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: DafnyToJavaTranslator <dafny-file> <output-java-file> [<package-name>]");
            System.exit(1);
        }

        String dafnyFile = args[0];
        String outputJavaFile = args[1];
        String packageName = args.length > 2 ? args[2] : "com.aws.verifier.examples";

        try {
            String dafnyCode = Files.readString(Paths.get(dafnyFile));
            DafnyToJavaTranslator translator = new DafnyToJavaTranslator();
            String javaCode = translator.translate(dafnyCode, packageName);
            Files.writeString(Paths.get(outputJavaFile), javaCode);
            System.out.println("Successfully translated " + dafnyFile + " to " + outputJavaFile);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public String translate(String dafnyCode, String packageName) {
        this.packageName = packageName;
        imports.clear();
        methods.clear();
        classes.clear();

        // Add default imports
        imports.add(JVERIFY_IMPORT);

        // Parse the Dafny code
        parseDafnyCode(dafnyCode);

        // Generate the Java code
        return generateJavaCode();
    }

    private void parseDafnyCode(String dafnyCode) {
        // Split the code into lines
        String[] lines = dafnyCode.split("\n");

        // Extract the class name from the file
        Pattern modulePattern = Pattern.compile("\\bmodule\\s+([A-Za-z0-9_]+)");
        for (String line : lines) {
            Matcher matcher = modulePattern.matcher(line);
            if (matcher.find()) {
                className = matcher.group(1);
                break;
            }
        }

        if (className == null) {
            // If no module found, try to find class or method declarations
            Pattern classPattern = Pattern.compile("\\bclass\\s+([A-Za-z0-9_]+)");
            for (String line : lines) {
                Matcher matcher = classPattern.matcher(line);
                if (matcher.find()) {
                    className = matcher.group(1);
                    break;
                }
            }
        }

        if (className == null) {
            // Default fallback
            className = "DafnyGenerated";
        }

        // Initialize the current class builder
        currentClassBuilder = new StringBuilder();
        currentClassBuilder.append("class ").append(className).append(" {\n");

        // Process method declarations
        processMethodDeclarations(lines);

        // Close the class
        currentClassBuilder.append("}\n");
        classes.add(currentClassBuilder.toString());
    }

    private void processMethodDeclarations(String[] lines) {
        // Patterns for method declarations
        Pattern methodPattern = Pattern.compile("\\bmethod\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:returns\\s*\\(([^)]*)\\))?");
        Pattern functionPattern = Pattern.compile("\\bfunction\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*:\\s*([A-Za-z0-9_<>]+)");
        Pattern predicatePattern = Pattern.compile("\\bpredicate\\s+([A-Za-z0-9_]+)(?:<[^>]*>)?\\s*\\(([^)]*)\\)");
        Pattern lemmaPredPattern = Pattern.compile("\\b(?:lemma|predicate)\\s+([A-Za-z0-9_]+)(?:<[^>]*>)?\\s*\\(([^)]*)\\)");

        StringBuilder currentMethod = null;
        String currentMethodName = null;
        boolean inMethod = false;
        boolean inRequires = false;
        boolean inEnsures = false;
        boolean inDecreases = false;
        boolean inMethodBody = false;
        int braceCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for method declaration
            Matcher methodMatcher = methodPattern.matcher(line);
            Matcher functionMatcher = functionPattern.matcher(line);
            Matcher predicateMatcher = predicatePattern.matcher(line);
            Matcher lemmaMatcher = lemmaPredPattern.matcher(line);

            if (methodMatcher.find() || functionMatcher.find() || predicateMatcher.find() || lemmaMatcher.find()) {
                // Start a new method
                inMethod = true;
                String methodName;
                String params;
                String returnType = "void";

                if (methodMatcher.find(0)) {
                    methodName = methodMatcher.group(1);
                    params = methodMatcher.group(2);
                    String returnParams = methodMatcher.group(3);
                    if (returnParams != null && !returnParams.trim().isEmpty()) {
                        returnType = convertDafnyTypeToJava(returnParams.trim().split("\\s*:\\s*")[1]);
                    }
                } else if (functionMatcher.find(0)) {
                    methodName = functionMatcher.group(1);
                    params = functionMatcher.group(2);
                    returnType = convertDafnyTypeToJava(functionMatcher.group(3));
                    // Add @Pure for functions
                    imports.add(PURE_IMPORT);
                } else if (predicateMatcher.find(0)) {
                    methodName = predicateMatcher.group(1);
                    params = predicateMatcher.group(2);
                    returnType = "boolean";
                    // Add @Pure for predicates
                    imports.add(PURE_IMPORT);
                } else {
                    methodName = lemmaMatcher.group(1);
                    params = lemmaMatcher.group(2);
                    // Add @Proof for lemmas
                    imports.add(PROOF_IMPORT);
                }

                currentMethodName = methodName;
                currentMethod = new StringBuilder();

                // Convert Dafny parameters to Java
                String javaParams = convertDafnyParamsToJava(params);

                // Add method signature
                if (returnType.contains("@Unbounded")) {
                    imports.add(UNBOUNDED_IMPORT);
                }
                if (returnType.contains("@Nat")) {
                    imports.add(NAT_IMPORT);
                }

                boolean isProof = lemmaMatcher.find(0);
                boolean isPure = functionMatcher.find(0) || predicateMatcher.find(0);

                if (isProof) {
                    currentMethod.append("    @Proof\n");
                }
                if (isPure) {
                    currentMethod.append("    @Pure\n");
                    currentMethod.append("    @Erased\n");
                    imports.add(ERASED_IMPORT);
                }

                currentMethod.append("    public static ").append(returnType).append(" ").append(methodName).append("(").append(javaParams).append(") {\n");

                inRequires = false;
                inEnsures = false;
                inDecreases = false;
                inMethodBody = false;
                braceCount = 0;
            } else if (inMethod) {
                // Check for requires, ensures, decreases clauses
                if (line.trim().startsWith("requires ")) {
                    inRequires = true;
                    inEnsures = false;
                    inDecreases = false;
                    inMethodBody = false;

                    String condition = line.trim().substring("requires ".length()).trim();
                    if (condition.endsWith(";")) {
                        condition = condition.substring(0, condition.length() - 1);
                    }

                    currentMethod.append("        precondition(").append(convertDafnyExpressionToJava(condition)).append(");\n");
                } else if (line.trim().startsWith("ensures ")) {
                    inRequires = false;
                    inEnsures = true;
                    inDecreases = false;
                    inMethodBody = false;

                    String condition = line.trim().substring("ensures ".length()).trim();
                    if (condition.endsWith(";")) {
                        condition = condition.substring(0, condition.length() - 1);
                    }

                    // Handle "old" expressions and result values
                    condition = condition.replace("old(", "old(");
                    condition = convertDafnyExpressionToJava(condition);

                    if (condition.contains("result")) {
                        currentMethod.append("        postcondition((").append(convertDafnyReturnTypeToJava(line)).append(" r) -> ")
                                .append(condition.replace("result", "r")).append(");\n");
                    } else {
                        currentMethod.append("        postcondition(() -> ").append(condition).append(");\n");
                    }
                } else if (line.trim().startsWith("decreases ")) {
                    inRequires = false;
                    inEnsures = false;
                    inDecreases = true;
                    inMethodBody = false;

                    String expr = line.trim().substring("decreases ".length()).trim();
                    if (expr.endsWith(";")) {
                        expr = expr.substring(0, expr.length() - 1);
                    }

                    currentMethod.append("        decrease(").append(convertDafnyExpressionToJava(expr)).append(");\n");
                } else if (line.trim().startsWith("{")) {
                    inRequires = false;
                    inEnsures = false;
                    inDecreases = false;
                    inMethodBody = true;
                    braceCount++;
                } else if (inMethodBody) {
                    // Process method body
                    if (line.contains("{")) {
                        braceCount += countOccurrences(line, '{');
                    }
                    if (line.contains("}")) {
                        braceCount -= countOccurrences(line, '}');
                    }

                    // Convert Dafny statements to Java
                    String javaLine = convertDafnyStatementToJava(line);
                    currentMethod.append("        ").append(javaLine).append("\n");

                    if (braceCount == 0) {
                        // End of method
                        inMethod = false;
                        methods.add(currentMethod.toString());
                        currentMethod.append("    }\n");
                        currentClassBuilder.append(currentMethod);
                        currentMethod = null;
                    }
                }
            }
        }

        // Handle incomplete method if any
        if (currentMethod != null) {
            currentMethod.append("    }\n");
            currentClassBuilder.append(currentMethod);
        }
    }

    private String convertDafnyParamsToJava(String params) {
        if (params == null || params.trim().isEmpty()) {
            return "";
        }

        String[] paramsList = params.split(",");
        List<String> javaParams = new ArrayList<>();

        for (String param : paramsList) {
            param = param.trim();
            if (param.isEmpty()) continue;

            String[] parts = param.split("\\s*:\\s*");
            if (parts.length < 2) continue;

            String name = parts[0].trim();
            String type = convertDafnyTypeToJava(parts[1].trim());

            javaParams.add(type + " " + name);
        }

        return String.join(", ", javaParams);
    }

    private String convertDafnyTypeToJava(String dafnyType) {
        dafnyType = dafnyType.trim();

        // Handle nat
        if (dafnyType.equals("nat")) {
            imports.add(NAT_IMPORT);
            return "@Nat int";
        }

        // Handle int
        if (dafnyType.equals("int")) {
            return "int";
        }

        // Handle bool
        if (dafnyType.equals("bool")) {
            return "boolean";
        }

        // Handle unbounded integers
        if (dafnyType.contains("nat") && dafnyType.contains("<!>")) {
            imports.add(NAT_IMPORT);
            imports.add(UNBOUNDED_IMPORT);
            return "@Unbounded @Nat int";
        }

        if (dafnyType.contains("int") && dafnyType.contains("<!>")) {
            imports.add(UNBOUNDED_IMPORT);
            return "@Unbounded int";
        }

        // Handle arrays
        if (dafnyType.startsWith("array")) {
            String innerType = dafnyType.substring(dafnyType.indexOf("<") + 1, dafnyType.lastIndexOf(">"));
            return convertDafnyTypeToJava(innerType) + "[]";
        }

        // Handle sequences
        if (dafnyType.startsWith("seq")) {
            return "sequence";
        }

        // Handle sets
        if (dafnyType.startsWith("set")) {
            return "set";
        }

        // Handle maps
        if (dafnyType.startsWith("map")) {
            return "map";
        }

        // Handle custom types and generic types
        if (dafnyType.contains("<")) {
            String baseType = dafnyType.substring(0, dafnyType.indexOf("<"));
            String params = dafnyType.substring(dafnyType.indexOf("<") + 1, dafnyType.lastIndexOf(">"));
            String[] typeParams = params.split(",");

            List<String> javaTypeParams = new ArrayList<>();
            for (String typeParam : typeParams) {
                javaTypeParams.add(convertDafnyTypeToJava(typeParam.trim()));
            }

            return baseType + "<" + String.join(", ", javaTypeParams) + ">";
        }

        // Default case
        return dafnyType;
    }

    private String convertDafnyReturnTypeToJava(String line) {
        // Extract the return type from ensures clause
        // This is a simplification, would need more context in a real implementation
        if (line.contains("result")) {
            return "Integer"; // Default to Integer for numeric results
        }
        return "Boolean"; // Default to Boolean for predicates
    }

    private String convertDafnyExpressionToJava(String expr) {
        // Replace Dafny expressions with JVerify equivalents
        expr = expr.replace("forall ", "forall(");
        expr = expr.replace(" :: ", " -> ");

        // Handle implication
        expr = expr.replace(" ==> ", " -> implies(");
        if (expr.contains("implies(")) {
            expr = expr + ")";
        }

        // Handle quantifiers
        if (expr.contains("forall(") && !expr.endsWith(")")) {
            expr = expr + ")";
        }

        // Handle sequence operations
        expr = expr.replace("[..]", "");
        expr = expr.replace("[]", "sequence()");

        // Handle set operations
        expr = expr.replace("{}", "set()");

        return expr;
    }

    private String convertDafnyStatementToJava(String stmt) {
        // Remove Dafny-specific syntax
        stmt = stmt.trim();

        // Handle variable declarations
        if (stmt.startsWith("var ")) {
            stmt = stmt.substring(4);
            String[] parts = stmt.split("\\s*:=\\s*");
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String varValue = convertDafnyExpressionToJava(parts[1].trim());

                if (varValue.endsWith(";")) {
                    varValue = varValue.substring(0, varValue.length() - 1);
                }

                // Infer type from value
                String type = "int"; // Default
                if (varValue.equals("true") || varValue.equals("false")) {
                    type = "boolean";
                } else if (varValue.contains("\"")) {
                    type = "String";
                }

                return type + " " + varName + " = " + varValue + ";";
            }
        }

        // Handle assert statements
        if (stmt.startsWith("assert ")) {
            String condition = stmt.substring("assert ".length());
            if (condition.endsWith(";")) {
                condition = condition.substring(0, condition.length() - 1);
            }
            return "assert(" + convertDafnyExpressionToJava(condition) + ");";
        }

        // Handle invariant statements
        if (stmt.startsWith("invariant ")) {
            String condition = stmt.substring("invariant ".length());
            if (condition.endsWith(";")) {
                condition = condition.substring(0, condition.length() - 1);
            }
            return "invariant(" + convertDafnyExpressionToJava(condition) + ");";
        }

        // Handle if statements
        if (stmt.startsWith("if ") && !stmt.contains("{")) {
            String condition = stmt.substring("if ".length());
            return "if (" + convertDafnyExpressionToJava(condition) + ") {";
        }

        // Handle while statements
        if (stmt.startsWith("while ") && !stmt.contains("{")) {
            String condition = stmt.substring("while ".length());
            return "while (" + convertDafnyExpressionToJava(condition) + ") {";
        }

        // Handle return statements
        if (stmt.startsWith("return ")) {
            String value = stmt.substring("return ".length());
            if (value.endsWith(";")) {
                value = value.substring(0, value.length() - 1);
            }
            return "return " + convertDafnyExpressionToJava(value) + ";";
        }

        // Handle reads statements
        if (stmt.startsWith("reads ")) {
            String objects = stmt.substring("reads ".length());
            if (objects.endsWith(";")) {
                objects = objects.substring(0, objects.length() - 1);
            }
            return "reads(" + convertDafnyExpressionToJava(objects) + ");";
        }

        // Handle modifies statements
        if (stmt.startsWith("modifies ")) {
            String objects = stmt.substring("modifies ".length());
            if (objects.endsWith(";")) {
                objects = objects.substring(0, objects.length() - 1);
            }
            return "modifies(" + convertDafnyExpressionToJava(objects) + ");";
        }

        // Default: return as is, adding semicolon if needed
        if (!stmt.endsWith(";") && !stmt.endsWith("}") && !stmt.endsWith("{") && !stmt.isEmpty()) {
            stmt = stmt + ";";
        }

        return stmt;
    }

    private String generateJavaCode() {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(packageName).append(";\n\n");

        // Import statements
        Set<String> uniqueImports = new LinkedHashSet<>(imports);
        for (String importStmt : uniqueImports) {
            sb.append(importStmt).append("\n");
        }
        sb.append("\n");

        // Classes and methods
        for (String classCode : classes) {
            sb.append(classCode).append("\n");
        }

        return sb.toString();
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }
}