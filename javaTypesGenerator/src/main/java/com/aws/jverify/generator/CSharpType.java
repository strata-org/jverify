package com.aws.jverify.generator;

import java.util.ArrayList;
import java.util.List;

public class CSharpType {
    String typeName;
    boolean isNullable;
    List<CSharpType> genericArguments;

    public CSharpType(String typeName, boolean isNullable) {
        this.typeName = typeName;
        this.isNullable = isNullable;
        this.genericArguments = new ArrayList<>();
    }

    public boolean hasGenericArguments() {
        return !genericArguments.isEmpty();
    }

    public static CSharpType parse(String typeStr) {
        boolean isNullable = typeStr.endsWith("?");
        if (isNullable) {
            typeStr = typeStr.substring(0, typeStr.length() - 1);
        }

        // Find the base type and any generic arguments
        int genericStart = typeStr.indexOf('<');
        if (genericStart == -1) {
            // Simple type with no generic arguments
            return new CSharpType(typeStr.trim(), isNullable);
        }

        // Extract the base type name
        String baseTypeName = typeStr.substring(0, genericStart).trim();
        CSharpType type = new CSharpType(baseTypeName, isNullable);

        // Extract and parse the generic arguments
        String genericArgsStr = typeStr.substring(genericStart + 1, typeStr.lastIndexOf('>'));
        List<String> genericArgs = splitGenericArgs(genericArgsStr);

        for (String arg : genericArgs) {
            type.genericArguments.add(parse(arg));
        }

        return type;
    }

    /**
     * Splits generic arguments, respecting nested generic types.
     * For example: "string, List<int>, Dictionary<string, int>" will be split into three arguments.
     */
    private static List<String> splitGenericArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(argsStr.substring(start, i).trim());
                start = i + 1;
            }
        }

        // Add the last argument
        if (start < argsStr.length()) {
            args.add(argsStr.substring(start).trim());
        }

        return args;
    }
}
