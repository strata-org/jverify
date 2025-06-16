package com.aws.jverify.verifier;

import com.aws.jverify.generated.Name;
import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import jdk.jshell.execution.Util;

import javax.xml.transform.OutputKeys;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Update the name of all method and field names of symbols within a compilation unit
 * to ensure uniqueness. The mangling algorithm is deterministic and works as follows:
 *   - a prefix ("_F") is added to a field name
 *   - a prefix ("_Z") is added to method names (except constructors)
 *   - a suffix is added to all method names. This suffix encodes the parameter types, as given by the typeMangling
 *     method
 */
public class NameMangler {
    static private final String fieldPrefix = "F_";
    static private final String methodPrefix = "Z_";
    public static final String CTOR_PREFIX = "_ctor_";

    // Map from symbols to mangled names
    private final Map<Symbol, String> symbolStringMap;
    private final Map<String, Symbol> reverseSymbolStringMap;

    private static String typeMangling(com.sun.tools.javac.code.Type type) {
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
                var classTypeStr = ((ClassType) type).toString();
                // Changing '.' to '_' so that the mangled name is a valid Dafny name
                // TODO: test this for more complicated class names, e.g. when JCTypeApply is supported
                return "C" + classTypeStr.replace('.','_');
            }
            case ARRAY : {
                var arrayType = (ArrayType) type;
                var componentType = arrayType.getComponentType();
                return "A" + typeMangling(componentType);
            }
            default : {
                return type.toString();
            }
        }
    }

    public NameMangler() {
        this.symbolStringMap = new HashMap<>();
        this.reverseSymbolStringMap = new HashMap<>();
    }

    public String safeUnmangleName(String name) {
        if (reverseSymbolStringMap.containsKey(name)) {
            return reverseSymbolStringMap.get(name).name.toString();
        }
        return name;
    }

    public String mangleSymbolName(Symbol s) {
        if (symbolStringMap.containsKey(s)) {
            return symbolStringMap.get(s);
        }
        var mangled = uncachedMangleSymbolName(s);
        reverseSymbolStringMap.put(mangled, s);
        return mangled;
    }
    
    private String uncachedMangleSymbolName(Symbol s) {
        switch (s) {
            case Symbol.ClassSymbol classSymbol -> {
                return classSymbol.getQualifiedName().toString().replace(".", "_");
            }
            case Symbol.MethodSymbol m -> {
                return getMethodName(m);
            }
            case Symbol.VarSymbol varSymbol -> {
                if (varSymbol.getKind().isField()) {
                    return mangleFieldName(varSymbol);
                } else { // Local variable, do not mangle
                    return s.name.toString();
                }
            }
            case null, default -> {
                return s.name.toString();
            }
        }
    }

    private String mangleFieldName(Symbol.VarSymbol s) {
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
        String baseName = s.name.toString();

        ClassNameStats result = getGetClassNameStats(s.enclClass(), s.name);
        if (baseName.contentEquals("<init>")) {
            Symbol.ClassSymbol enclosingClass = s.enclClass();
            baseName = getConstructorName(enclosingClass);
        }
        else {
            if (result.sameNameFields()) {
                baseName = methodPrefix + baseName;
            }
        }
        if (result.methodsWithThisName() == 1) {
            return baseName;
        }
        List<Type> argTypes;
        if (s.type instanceof MethodType m) {
            argTypes = m.getParameterTypes();
        } else if (s.type instanceof DelegatedType dt){
            argTypes = dt.getParameterTypes();
        } else {
            // Throw an exception
            throw new IllegalArgumentException(s.toString());
        }
        baseName = (argTypes.isEmpty()) ? baseName : baseName+"_";
        for (var param : argTypes) {
            baseName += typeMangling(param);
        }
        return baseName;
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
