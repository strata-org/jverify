package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class NullableGenerator extends WrappingDafnyGenerator {
    private final BaseDafnyGenerator baseGenerator;
    private final Reporter reporter;
    
    public NullableGenerator(BaseDafnyGenerator baseGenerator, DafnyGenerator next) {
        super(next);
        this.baseGenerator = baseGenerator;
        reporter = baseGenerator.reporter;
    }

    @Override
    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin, JCTree.JCModifiers additionalModifiers) {

        var isNullable = isNullable(type, additionalModifiers);
        var primitiveTypeKind = baseGenerator.toPrimitiveType(type);
        if (primitiveTypeKind != null && isNullable) {
            reporter.reportError(origin, "notSupported", "nullable primitive type");
        }
        return super.translateType(type, origin, additionalModifiers);
    }

    private static String getNullableSuffix(boolean isNullable) {
        return isNullable ? "?" : "";
    }

    private boolean isNullable(JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCIdent identifier) {
            return isNullable(identifier.sym.type);
        } else if (expression instanceof JCTree.JCFieldAccess access) {
            return isNullable(access.sym.type);
        }
        return isNullable(expression.type);
    }
    
    public static boolean isNullable(com.sun.tools.javac.code.Type type, JCTree.JCModifiers additionalModifiers) {
        // In several cases annotations that come right before types
        // end up bound to tree nodes such as variable declarations instead of the type.
        // Hence, for something like `@Nullable int[] foo;`, which should be interpreted as `(@Nullable int)[] foo;`,
        // we apply the modifier to the innermost element type of an array type.
        return isNullable(type) || (isNullable(additionalModifiers) && !(type instanceof com.sun.tools.javac.code.Type.ArrayType));
    }

    @Override
    public UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayType, 
                                              IOrigin origin, 
                                              JCTree.JCModifiers additionalModifiers) {
        var isNullable = isNullable(arrayType, additionalModifiers);
        var result = super.translateArrayType(arrayType, origin, additionalModifiers);
        return addQuestionMarkToUserDefinedType(result, isNullable);
    }

    private static UserDefinedType addQuestionMarkToUserDefinedType(UserDefinedType original, boolean nullable) {
        if (!nullable) {
            return original;
        }
        var nullableSuffix = getNullableSuffix(nullable);
        var originalNamePath = (NameSegment) original.getNamePath();
        return new UserDefinedType(original.getOrigin(), new NameSegment(originalNamePath.getOrigin(),
                originalNamePath.getName() + nullableSuffix, originalNamePath.getOptTypeArguments()));
    }

    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType type) {
        var isNullable = isNullable(type, additionalModifiers);
        var immutable = this.baseGenerator.isImmutable((Symbol.ClassSymbol) type.tsym);
        var originalResult = (UserDefinedType) next.translateClassType(origin, additionalModifiers, type);
        if (isNullable && immutable) {
            return new UserDefinedType(origin, new NameSegment(origin, "Nullable", List.of(originalResult)));
        } else {
            return addQuestionMarkToUserDefinedType(originalResult, isNullable);
        }
    }

    public static boolean isNullable(JCTree.JCModifiers modifiers) {
        return BaseDafnyGenerator.isAnnotated(modifiers, com.aws.jverify.Nullable.class);
    }

    public static boolean isNullable(com.sun.tools.javac.code.Type type) {
        return BaseDafnyGenerator.isAnnotated(type, com.aws.jverify.Nullable.class);
    }

    @Override
    public Expression translateLiteral(JCTree.JCLiteral literal, IOrigin origin,
                                       ExpressionContext context) {
        if (literal.getValue() == null) {
            var immutable = context.fallbackType() != null && context.fallbackType().tsym instanceof Symbol.ClassSymbol classSymbol &&
                    this.baseGenerator.isImmutable(classSymbol);
            if (immutable) {
                return baseGenerator.expressionCompiler.createCall(origin,
                        new NameSegment(origin, "Null", null), Stream.empty(), context);
            }
        }
        return super.translateLiteral(literal, origin, context);
    }

    @Override
    public Expression toExpr(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        var origin = baseGenerator.toOrigin(expr);
        if (expr instanceof JCTree.JCMethodInvocation invocation) {
            if (invocation.meth instanceof JCTree.JCFieldAccess fieldAccess) {
                var isNullable = isNullable(fieldAccess.getExpression().type);
                var isImmutable = baseGenerator.isImmutable((Symbol.ClassSymbol) fieldAccess.getExpression().type.tsym);
                if (isNullable && isImmutable) {
                    var nullableCalleeTarget = baseGenerator.expressionCompiler.toExpr(fieldAccess.getExpression(), null);
                    var nonNullCalleeTarget = new ExprDotName(origin, nullableCalleeTarget, new Name(origin, "value"), null);
                    var nonNullCallee = new ExprDotName(origin, nonNullCalleeTarget, baseGenerator.getName(fieldAccess, fieldAccess.name), null);
                    return baseGenerator.expressionCompiler.createCall(origin, nonNullCallee, invocation.getArguments().stream(), context);
                }
            }
        } else if (expr instanceof JCTree.JCAssign assign) {
            var isImmutable = baseGenerator.isImmutable((Symbol.ClassSymbol) assign.getExpression().type.tsym);
            if (isImmutable) {
                var nullableTarget = isNullable(assign.getVariable());
                var nullableValue = isNullable(assign.getExpression());
                if (!nullableTarget && nullableValue) {
                    Expression target = baseGenerator.expressionCompiler.toExpr(assign.getVariable(), context);
                    List<Expression> lhss = List.of(target);
                    var valueExpr = baseGenerator.getFinalGenerator().toExpr(assign.getExpression(), origin, context);
                    var nonNullValueExpr = new ExprDotName(origin, valueExpr, new Name(origin, "value"), null);
                    List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, nonNullValueExpr));
                    context.statementWriter().accept(new AssignStatement(origin, null, lhss, rhss, false));
                    return target;
                } else if (nullableTarget && !nullableValue) {
                    Expression target = baseGenerator.expressionCompiler.toExpr(assign.getVariable(), context);
                    List<Expression> lhss = List.of(target);
                    var nonNullValueExpr = baseGenerator.getFinalGenerator().toExpr(assign.getExpression(), origin, context);
                    var nullableValueExpr = ExpressionCompiler.createCall2(origin, new NameSegment(origin, "NonNull", null), Stream.of(nonNullValueExpr));
                    List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, nullableValueExpr));
                    context.statementWriter().accept(new AssignStatement(origin, null, lhss, rhss, false));
                    return target;
                }
            }
        }
        return super.toExpr(expr, originOverride, context);
    }

    @Override
    public List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin originOverride) {
        
        return super.translateStatementAfterLabel(blockCompiler, statement, labels, originOverride);
    }
}
