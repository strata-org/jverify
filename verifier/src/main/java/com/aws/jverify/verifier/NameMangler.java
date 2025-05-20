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
            case CLASS: {return "C" + ((ClassType)type);}
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


    private String mangleSymbolName(Symbol s) {
        return symbolStringMap.computeIfAbsent(s, this::uncachedMangleSymbolName);
    }
    private String uncachedMangleSymbolName(Symbol s) {
        if (s instanceof Symbol.MethodSymbol m) {
            return mangleMethodName(s);
        } else if (s instanceof Symbol.VarSymbol varSymbol){
            if (varSymbol.getKind().isField()) {
                return mangleFieldName(s);
            }
            else { // Local variable, do not mangle
                return s.name.toString();
            }
        } else {
            return s.name.toString();
        }
    }

    private String mangleFieldName(Symbol s) {
        if (s.name.contentEquals("this")) {
            return s.name.toString();
        }
        String newName = fieldPrefix + s.name.toString();
        return newName;
    }

    private String mangleMethodName(Symbol s) {
        String baseName = s.name.toString();
        if (baseName.contentEquals("<init>")) {
            baseName = "_ctor";
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

    public String getName(Tree tree) {
        if (tree instanceof JCTree.JCMethodDecl methodDecl) {
            return mangleSymbolName(methodDecl.sym);
        }
        else if (tree instanceof JCTree.JCVariableDecl variableDecl) {
            return mangleSymbolName(variableDecl.sym);
        }
        else if (tree instanceof JCTree.JCIdent ident) {
            return mangleSymbolName(ident.sym);
        }
        else if (tree instanceof JCTree.JCNewClass newClass) {
            return mangleSymbolName(newClass.constructor);
        }
        else if (tree instanceof JCTree.JCFieldAccess fieldAccess) {
            return mangleSymbolName(fieldAccess.sym);
        }
        else if (tree instanceof JCTree.JCMethodDecl methodDecl) {
            return mangleSymbolName(methodDecl.sym);
        }
        else {
            throw new IllegalArgumentException(tree.toString());
        }
    }
}
