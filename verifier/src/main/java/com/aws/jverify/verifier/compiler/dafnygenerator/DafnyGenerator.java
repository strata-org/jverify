package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface DafnyGenerator {
    FilesContainer generateDafny(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries);
    
    List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin originOverride);
    
    List<Statement> translateStatementMethodInvocation(BlockCompiler blockCompiler, JCTree.JCMethodInvocation invocation, ExpressionContext expressionContext);

    Expression translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin origin, ExpressionContext context);

    Expression toExpr(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context);
        
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

    Expression translateLiteral(JCTree.JCLiteral literal, IOrigin origin, ExpressionContext context);
    
    AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context);
    
    static DafnyGenerator getGenerator(Context context) {
        var base = new BaseDafnyGenerator(context);
        var result = new ModifiableObjectGenerator(context, base, 
                new JVerifyGhostExpressionCompiler(new NullableGenerator(base, base), base));
        base.setFinalGenerator(result);
        return result;
    }
}
