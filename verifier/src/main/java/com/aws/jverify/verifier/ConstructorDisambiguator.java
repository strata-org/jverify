package com.aws.jverify.verifier;

import com.aws.jverify.Contract;
import com.aws.jverify.generated.Name;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.stream.Collectors;

public class ConstructorDisambiguator {
    private JavaToDafnyCompiler javaToDafnyCompiler;
    private HashMap<Symbol.@Nullable MethodSymbol, Name> ctorNames;
    private int ctorNum;
    private HashMap<JCTree.JCExpression, String> classesDefiningContracts;

    public ConstructorDisambiguator(JavaToDafnyCompiler javaToDafnyCompiler) {
        ctorNames = new HashMap<>();
        ctorNum = 0;
        this.javaToDafnyCompiler = javaToDafnyCompiler;
        classesDefiningContracts = new HashMap<>();
    }

    public void handleCompilationUnit(JCTree.JCCompilationUnit compilationUnit) {
        //this.compilationUnit = compilationUnit;

        for (var typeDecl : compilationUnit.getTypeDecls()) {
            handleTypeDecl(typeDecl);
        }
    }


    private static Symbol.ClassSymbol getClassSymbol(JCTree.JCExpression valueArgument) {
        Symbol.ClassSymbol classSymbol;
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess &&
                fieldAccess.selected instanceof JCTree.JCIdent ident &&
                ident.sym instanceof Symbol.ClassSymbol classSymbol2)
        {
            classSymbol = classSymbol2;
        } else {
            throw new JavaViolationException();
        }
        return classSymbol;
    }

    private void handleTypeDecl(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            System.out.println("Class " + classDecl.name);
            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));
            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            Symbol.@Nullable ClassSymbol oldClass = null;
            if (contractAnnotation != null) {
                var arguments = this.javaToDafnyCompiler.getArguments(contractAnnotation);
                oldClass = getClassSymbol(arguments.get("value"));
                //this.classesDefiningContracts.put(oldClass, classDecl.name);
            }
            if (this.javaToDafnyCompiler.classWithExternalContract.contains(classDecl.sym)) {
                return;
            }
            for (var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    Names names = Names.instance(this.javaToDafnyCompiler.context);
                    //methodDecl.sym.name = true ? methodDecl.sym.name : names.fromString("toto"+(ctorNum++));
                    /*if (TreeInfo.isConstructor(methodDecl)) {
                        addCtor(oldClass, methodDecl);
                        var ctorName = new Name(this.javaToDafnyCompiler.toOrigin(member), "m_ctor" + (ctorNum++));
                        System.out.println("Constructor " + methodDecl.sym + " ->" + ctorName);
                        ctorNames.put(methodDecl.sym, ctorName);
                    }*/
                }
            }
        }
    }
/*
    public Name getNameForSymbol(JCTree.JCMethodDecl methodDecl) {
        return ctorNames.get(methodDecl.sym);
    }

    public Name getNameForSymbol(JCTree.JCNewClass newClass) {
        var tmp = classesDefiningContracts.get(newClass.clazz);
        if (tmp != null) {
            var ctorSymbol = findSymbolWithType(newClass.clazz, newClass.constructorType);
        }
        else {
            return ctorNames.get(newClass.constructor);
        }
    }

 */


}
