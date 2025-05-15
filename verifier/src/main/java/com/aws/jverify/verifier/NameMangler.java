package com.aws.jverify.verifier;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type.*;

import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.tree.JCTree;

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
    }

    public void mangleNames(JCTree.JCCompilationUnit compilationUnit) {
        for (var typeDecl : compilationUnit.getTypeDecls()) {
            mangleNames(typeDecl);
        }
    }

    private void mangleNames(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            for (var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    String baseName = methodDecl.sym.name.toString();
                    if (baseName.contentEquals("<init>")) {
                        baseName = "ctor";
                    }
                    else {
                        baseName = methodPrefix + baseName;
                    }
                    MethodType methodType = null;
                    if (methodDecl.type instanceof MethodType m) {
                        methodType = m;
                    } else {
                        this.javaToDafnyCompiler.reportError(methodDecl, "notSupported",  "parametrized method: " + methodDecl.name.toString());
                        return;
                    }
                    var argTypes = methodType.getParameterTypes();
                    baseName = (argTypes.isEmpty()) ? baseName : baseName+"_";
                    for (var param : argTypes) {
                        baseName += typeMangling(param);
                    }
                    methodDecl.sym.name = nameFactory.fromString(baseName);
                }
                else if (member instanceof JCTree.JCVariableDecl variableDecl) {
                    String newName = fieldPrefix + variableDecl.sym.name.toString();
                    variableDecl.sym.name = nameFactory.fromString(newName);
                }
            }
        }
    }
}
