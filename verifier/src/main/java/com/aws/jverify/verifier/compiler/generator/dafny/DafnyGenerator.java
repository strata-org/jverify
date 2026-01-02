package com.aws.jverify.verifier.compiler.generator.dafny;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface DafnyGenerator {
    FilesContainer generateDafny(List<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries);

    List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin originOverride);

    default Expression toExpr(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        return toExprWithFlows(expr, originOverride, context).expression();
    }

    ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context);

    @Nullable
    Type translateType(com.sun.tools.javac.code.Type type, 
                       IOrigin origin, 
                       JCTree.JCModifiers additionalModifiers);

    UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayTypeTree, 
                                       IOrigin origin, 
                                       JCTree.JCModifiers additionalModifiers);

    Type translateClassType(IOrigin origin,
                       JCTree.JCModifiers additionalModifiers,
                       com.sun.tools.javac.code.Type.ClassType classType);

    AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context);

    static DafnyGenerator getGenerator(Context context) {
        var result = new WrappingDafnyGenerator(null);
        context.put(DafnyGenerator.class, result);

        var base = new BaseDafnyGenerator(context);
        result.next = new ImpureObjectGenerator(context, base, 
                new JVerifyGhostExpressionCompiler(context, new NullableGenerator(base, base)));
        return result;
    }
}
