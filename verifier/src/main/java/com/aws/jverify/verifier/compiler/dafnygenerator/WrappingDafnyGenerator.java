package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Set;

public class WrappingDafnyGenerator implements DafnyGenerator {
    protected final DafnyGenerator next;

    public WrappingDafnyGenerator(DafnyGenerator next) {
        this.next = next;
    }

    @Override
    public FilesContainer generateDafny(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {
        return next.generateDafny(parsed, libraries);
    }

    @Override
    public Expression translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin origin) {
        return next.translateMethodInvocation(invocation, origin);
    }

    @Override
    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, 
                                        IOrigin origin, 
                                        JCTree.JCModifiers additionalModifiers) {
        return next.translateType(type, origin, additionalModifiers);
    }

    @Override
    public UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayTypeTree, 
                                              IOrigin origin, 
                                              JCTree.JCModifiers additionalModifiers) {
        return next.translateArrayType(arrayTypeTree, origin, additionalModifiers);
    }

    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType classType) {
        return next.translateClassType(origin, additionalModifiers, classType);
    }

    @Override
    public AssignmentRhs translateNewClassToAssignmentRhs(BlockCompiler blockCompiler, JCTree.JCNewClass newClass, IOrigin origin) {
        return next.translateNewClassToAssignmentRhs(blockCompiler, newClass, origin);
    }
}
