package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.lang.model.element.ElementKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

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
    public String DEFAULT_CTOR_NAME = "ctor";
    public static final String NON_DEFAULT_CTOR_NAME = "ctor";
    public String METHOD_RETURN_VARIABLE_NAME = "g_result";
    public String CLASS_PREFIX = "Constructable_";
    public String INIT_METHOD_PREFIX = "init_";
    public String IMPLEMENTATION_METHOD_PREFIX = "impl_";

    private final Map<com.sun.tools.javac.util.Name, Integer> classNameOccurrenceCounts = new HashMap<>();
    private final Set<Name> preludeReferencedClassNames = new HashSet<>();
    private final Map<Symbol, String> symbolStringMap;
    private final Map<String, Symbol> reverseSymbolStringMap;
    private final boolean avoidCollisionsUsingUnderscores;

    public NameCompiler(boolean avoidCollisionsUsingUnderscores) {
        this.avoidCollisionsUsingUnderscores = avoidCollisionsUsingUnderscores;
        if (this.avoidCollisionsUsingUnderscores) {
            DEFAULT_CTOR_NAME += "_";
            INIT_METHOD_PREFIX += "_";
            CLASS_PREFIX += "_";
            IMPLEMENTATION_METHOD_PREFIX += "_";
            METHOD_RETURN_VARIABLE_NAME += "#";
        }
        this.symbolStringMap = new HashMap<>();
        this.reverseSymbolStringMap = new HashMap<>();
    }
    
    public void registerClass(Symbol.ClassSymbol classSymbol) {
        classNameOccurrenceCounts.merge(classSymbol.name, 1, (a, b) -> a + 1);
    }

    public void registerPreludeReferencedName(Name name) {
        preludeReferencedClassNames.add(name);
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
                var occurrenceCount = classNameOccurrenceCounts.get(classSymbol.name);
                var preludeReferenced = preludeReferencedClassNames.contains(classSymbol.name);
                if (preludeReferenced || occurrenceCount == null || occurrenceCount > 1) {
                    return classSymbol.getQualifiedName().toString().replace(".", "_");
                }
                return classSymbol.name.toString();
            }
            case Symbol.MethodSymbol m -> {
                return getMethodName(m);
            }
            case Symbol.VarSymbol varSymbol -> {
                if (varSymbol.getKind().isField()
                        || varSymbol.getKind() == ElementKind.RECORD_COMPONENT) {
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
        boolean willSuffixWithArguments = classStats.methodsWithThisName() == 1;
        if (s.name.equals(s.name.table.names.init)) {
            result.append(getConstructorName(willSuffixWithArguments));
        }
        else {
            if (classStats.sameNameFields()) {
                result.append(methodPrefix);
            }
            result.append(s.name);
        }
        if (willSuffixWithArguments) {
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

    public String getConstructorName(boolean nonDefaultConstructor) {
        return nonDefaultConstructor ? NON_DEFAULT_CTOR_NAME : DEFAULT_CTOR_NAME;
    }

    public String getInitMethodName(String className, String constructorName) {
        return constructorName.replace("ctor", INIT_METHOD_PREFIX) + "_" + className;
    }
}
