package com.aws.jverify.verifier;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type.*;

import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.tree.JCTree;

import java.util.HashSet;

/**
 * Update the name of all method and field names of symbols within a compilation unit
 * to ensure uniqueness. The mangling algorithm is deterministic and works as follows:
 *   - a prefix ("_F") is added to a field name
 *   - a prefix ("_Z") is added to method names (except constructors)
 *   - a suffix is added to all method names. This suffix encodes the parameter types, as given by the typeMangling
 *     method
 */
public class NameMangler {
    private final JavaToDafnyCompiler javaToDafnyCompiler;
    private Names nameFactory;
    static private final String fieldPrefix = "F_";
    static private final String methodPrefix = "Z_";

    // Cache of all symbols whose names were already mangled.
    private HashSet<Symbol> mangledSymbols;

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
            case CLASS: {return "C" + ((ClassType)type).toString();}
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

    public NameMangler(JavaToDafnyCompiler javaToDafnyCompiler) {
        this.javaToDafnyCompiler = javaToDafnyCompiler;
        this.nameFactory = Names.instance(this.javaToDafnyCompiler.context);
        this.mangledSymbols = new HashSet<com.sun.tools.javac.code.Symbol>();
    }


    public String getSymbolName(Symbol s) {
        if (mangledSymbols.contains(s)) {
            return s.name.toString();
        }
        String newName;
        if (s.type instanceof MethodType m) {
            newName = getMethodName(s);
        } else { // TODO: ensure this is a field?
            newName = getFieldName(s);
        }
        s.name = nameFactory.fromString(newName);
        mangledSymbols.add(s);
        return newName;
    }

    private String getFieldName(Symbol s) {
        String newName = fieldPrefix + s.name.toString();
        return newName;
    }

    private String getMethodName(Symbol s) {
        String baseName = s.name.toString();
        if (baseName.contentEquals("<init>")) {
            baseName = "_ctor";
        }
        else {
            baseName = methodPrefix + baseName;
        }
        MethodType methodType = null;
        if (s.type instanceof MethodType m) {
            methodType = m;
        } else {
            assert(false);
        }
        var argTypes = methodType.getParameterTypes();
        baseName = (argTypes.isEmpty()) ? baseName : baseName+"_";
        for (var param : argTypes) {
            baseName += typeMangling(param);
        }
        return baseName;
    }

    public void mangleNames(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            for (var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    getSymbolName(methodDecl.sym);
                }
                else if (member instanceof JCTree.JCVariableDecl variableDecl) {
                    getSymbolName(variableDecl.sym);
                }
            }
        }
    }
}
