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

    private HashSet<Symbol> symbols;

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
        this.symbols = new HashSet<com.sun.tools.javac.code.Symbol>();
    }


    public String getFieldName(Symbol s) {
        if (s.type instanceof MethodType m) {
            return getMethodName(s);
        }
        if (symbols.contains(s)) {
            return s.name.toString();
        }
        symbols.add(s);
        String newName = fieldPrefix + s.name.toString();
        s.name = nameFactory.fromString(newName);
        return s.name.toString();
    }

    public String getMethodName(Symbol s) {
        if (symbols.contains(s)) {
            return s.name.toString();
        }
        symbols.add(s);

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
            return s.name.toString(); // Missing error message
        }
        var argTypes = methodType.getParameterTypes();
        baseName = (argTypes.isEmpty()) ? baseName : baseName+"_";
        for (var param : argTypes) {
            baseName += typeMangling(param);
        }
        s.name = nameFactory.fromString(baseName);
        return s.name.toString();
    }

    public void mangleNames(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            for (var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    getMethodName(methodDecl.sym);
                }
                else if (member instanceof JCTree.JCVariableDecl variableDecl) {
                    getFieldName(variableDecl.sym);
                }
            }
        }
    }
}
