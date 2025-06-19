package com.aws.jverify.verifier;

import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Maps names of symbols (classes, methods, fields) to compiled names to ensure uniqueness at the Dafny level.
 * The name mapping works as follows:
 *   - For fields that conflict with method names: prefix "F_" is added
 *   - For methods that conflict with field names: prefix "Z_" is added
 *   - For constructors: prefix "_ctor_" followed by the class name is used
 *   - For overloaded methods: a suffix encoding parameter types is added (e.g. "_iCjava_lang_String")
 *   - For classes with name collisions: fully qualified name with dots replaced by underscores
 * 
 * This class maintains bidirectional mappings between original and compiled names,
 * allowing for name resolution in both directions.
 */
public class NameCompiler {
    static private final String fieldPrefix = "F_";
    static private final String methodPrefix = "Z_";
    public static final String CTOR_PREFIX = "_ctor_";

    private final Map<com.sun.tools.javac.util.Name, Integer> classNameOccurrenceCounts = new HashMap<>();
    private final Map<Symbol, String> symbolStringMap;
    private final Map<String, Symbol> reverseSymbolStringMap;

    public NameCompiler() {
        this.symbolStringMap = new HashMap<>();
        this.reverseSymbolStringMap = new HashMap<>();
    }
    
    public void registerClass(Symbol.ClassSymbol classSymbol) {
        classNameOccurrenceCounts.merge(classSymbol.name, 1, (a, b) -> a + 1);
    }

    public String safeGetOriginalName(String name) {
        if (reverseSymbolStringMap.containsKey(name)) {
            return reverseSymbolStringMap.get(name).name.toString();
        }
        return name;
    }

    public String getCompiledName(Symbol s) {
        if (symbolStringMap.containsKey(s)) {
            return symbolStringMap.get(s);
        }
        var compiledName = uncachedGetCompiledName(s);
        symbolStringMap.put(s, compiledName);
        reverseSymbolStringMap.put(compiledName, s);
        return compiledName;
    }
    
    private String uncachedGetCompiledName(Symbol s) {
        switch (s) {
            case Symbol.ClassSymbol classSymbol -> {
                if (classNameOccurrenceCounts.computeIfAbsent(classSymbol.name, _ -> 2) > 1) {
                    return classSymbol.getQualifiedName().toString().replace(".", "_");
                }
                return classSymbol.name.toString();
            }
            case Symbol.MethodSymbol m -> {
                return getMethodName(m);
            }
            case Symbol.VarSymbol varSymbol -> {
                if (varSymbol.getKind().isField()) {
                    return getFieldName(varSymbol);
                } else { // Local variable, do not change
                    return s.name.toString();
                }
            }
            default -> {
                return s.name.toString();
            }
        }
    }

    private String getFieldName(Symbol.VarSymbol s) {
        if (s.name.contentEquals("this")) {
            return s.name.toString();
        }
        var classStats = getGetClassNameStats(s.enclClass(), s.name);
        if (classStats.methodsWithThisName > 0) {
            return fieldPrefix + s.name.toString();
        }
        return s.name.toString();
    }

    private String getMethodName(Symbol.MethodSymbol s) {
        StringBuilder result = new StringBuilder();
                
        ClassNameStats classStats = getGetClassNameStats(s.enclClass(), s.name);
        if (s.name.equals(s.name.table.names.init)) {
            Symbol.ClassSymbol enclosingClass = s.enclClass();
            result.append(getConstructorName(enclosingClass));
        }
        else {
            if (classStats.sameNameFields()) {
                result.append(methodPrefix);
            }
            result.append(s.name);
        }
        if (classStats.methodsWithThisName() == 1) {
            return result.toString();
        }
        addArgumentTypes(s, result);
        return result.toString();
    }

    private static void addArgumentTypes(Symbol.MethodSymbol method, StringBuilder result) {
        List<Type> argTypes;
        if (method.type instanceof MethodType m) {
            argTypes = m.getParameterTypes();
        } else if (method.type instanceof DelegatedType dt) {
            argTypes = dt.getParameterTypes();
        } else {
            throw new IllegalArgumentException(method.toString());
        }
        if (!argTypes.isEmpty()) {
            result.append("_");
            for (var param : argTypes) {
                result.append(getShortTypeName(param));
            }
        }
    }

    private static String getShortTypeName(com.sun.tools.javac.code.Type type) {
        switch (type.getTag()) {
            case VOID : {return "v";}
            case BYTE : {return "b";}
            case CHAR : {return "c";}
            case SHORT: {return "s";}
            case INT : {return "i";}
            case LONG : {return "l";}
            case FLOAT :{return "f";}
            case DOUBLE : {return "d";}
            case BOOLEAN: {return "z";}
            case CLASS: {
                var classTypeStr = type.toString();
                // Changing '.' to '_' so that the new name is a valid Dafny name
                // TODO: test this for more complicated class names, e.g. when JCTypeApply is supported
                return "C" + classTypeStr.replace('.','_');
            }
            case ARRAY : {
                var arrayType = (ArrayType) type;
                var componentType = arrayType.getComponentType();
                return "A" + getShortTypeName(componentType);
            }
            default : {
                return type.toString();
            }
        }
    }

    private static ClassNameStats getGetClassNameStats(Symbol.ClassSymbol clazz, com.sun.tools.javac.util.Name name) {
        boolean sameNameFields = false;
        int methodsWithThisName = 0;
        for(var member : clazz.members().getSymbolsByName(name)) {
            if (member instanceof Symbol.VarSymbol) {
                sameNameFields = true;
            }
            if (member instanceof Symbol.MethodSymbol) {
                methodsWithThisName += 1;
            }
        }
        return new ClassNameStats(sameNameFields, methodsWithThisName);
    }

    private record ClassNameStats(boolean sameNameFields, int methodsWithThisName) {}

    public static String getConstructorName(Symbol.ClassSymbol enclosingClass) {
        return CTOR_PREFIX + enclosingClass.name.toString();
    }
}
