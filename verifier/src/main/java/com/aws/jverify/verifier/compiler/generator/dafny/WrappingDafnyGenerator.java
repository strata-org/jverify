package com.aws.jverify.verifier.compiler.generator.dafny;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WrappingDafnyGenerator implements DafnyGenerator {
    protected DafnyGenerator next;

    public WrappingDafnyGenerator(DafnyGenerator next) {
        this.next = next;
    }

    @Override
    public FilesContainer generateDafny(List<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {
        return next.generateDafny(parsed, libraries);
    }

    @Override
    public List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin originOverride) {
        return next.translateStatementAfterLabel(blockCompiler, statement, labels, originOverride);
    }

    @Override
    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        return next.toExprWithFlows(expr, originOverride, context);
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
    public AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context) {
        return next.translateNewClassToAssignmentRhs(newClass, origin, context);
    }
}
