package com.aws.jverify.verifier;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;



import com.sun.tools.javac.tree.JCTree;

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

    // Map from symbols to mangled names
    private final Map<Symbol, String> symbolStringMap;

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
    }


    public String mangleSymbolName(Symbol s) {
        return symbolStringMap.computeIfAbsent(s, this::uncachedMangleSymbolName);
    }
    private String uncachedMangleSymbolName(Symbol s) {
        switch (s) {
            case Symbol.ClassSymbol classSymbol -> {
                return classSymbol.getQualifiedName().toString().replace(".", "_");
            }
            case Symbol.MethodSymbol m -> {
                return mangleMethodName(s);
            }
            case Symbol.VarSymbol varSymbol -> {
                if (varSymbol.getKind().isField()) {
                    return mangleFieldName(s);
                } else { // Local variable, do not mangle
                    return s.name.toString();
                }
            }
            case null, default -> {
                return s.name.toString();
            }
        }
    }

    private String mangleFieldName(Symbol s) {
        if (s.name.contentEquals("this")) {
            return s.name.toString();
        }
        return fieldPrefix + s.name.toString();
    }

    private String mangleMethodName(Symbol s) {
        String baseName = s.name.toString();
        if (baseName.contentEquals("<init>")) {
            baseName = "_ctor" + s.enclClass().name.toString();
        }
        else {
            baseName = methodPrefix + baseName;
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
}
