package com.aws.jverify.verifier;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.generated.Name;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Update the name of all methodDecl symbols to ensure uniqueness
 */
public class ConstructorDisambiguator {
    private final JavaToDafnyCompiler javaToDafnyCompiler;
    private int ctorNum;

    public ConstructorDisambiguator(JavaToDafnyCompiler javaToDafnyCompiler) {
        this.javaToDafnyCompiler = javaToDafnyCompiler;
        ctorNum = 0;
    }

    public void handleCompilationUnit(JCTree.JCCompilationUnit compilationUnit) {
        for (var typeDecl : compilationUnit.getTypeDecls()) {
            handleTypeDecl(typeDecl);
        }
    }


    private void handleTypeDecl(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                                        (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                                        a -> a));
            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            var verifyAnnotation = annotationsByName.get(Verify.class.getName());
            var should = true;
            if (verifyAnnotation != null) {
                var arguments = this.javaToDafnyCompiler.getArguments(verifyAnnotation);
                var shouldArgument = arguments.get("value");
                if (shouldArgument != null) {
                    should = (boolean) JavaToDafnyCompiler.getLiteralValue(shouldArgument);
                }
            }
            if (contractAnnotation != null || !should) {
                return; // Limitation. To remove with a deterministic mangling algorithm that does not depend on the base class
            }

            for (var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    // Need to find a good mangling algorithm
                    Names names = Names.instance(this.javaToDafnyCompiler.context);
                    methodDecl.sym.name = names.fromString("toto" + (ctorNum++));
                }
            }
        }
    }
}
