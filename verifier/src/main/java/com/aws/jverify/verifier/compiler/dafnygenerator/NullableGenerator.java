package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;
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
        return makeUserDefinedTypeNullable(result, isNullable, false);
    }

    private static UserDefinedType makeUserDefinedTypeNullable(UserDefinedType original, boolean nullable, boolean immutable) {

        if (nullable && immutable) {
            return new UserDefinedType(original.getOrigin(), 
                    new NameSegment(original.getOrigin(), "Nullable", List.of(original)));
        }
        
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
        var immutable = this.baseGenerator.isPure((Symbol.ClassSymbol) type.tsym);
        var originalResult = (UserDefinedType) next.translateClassType(origin, additionalModifiers, type);
        return makeUserDefinedTypeNullable(originalResult, isNullable, immutable);
    }

    public static boolean isNullable(JCTree.JCModifiers modifiers) {
        return BaseDafnyGenerator.isAnnotated(modifiers, com.aws.jverify.Nullable.class);
    }

    public static boolean isNullable(com.sun.tools.javac.code.Type type) {
        return BaseDafnyGenerator.isAnnotated(type, com.aws.jverify.Nullable.class);
    }

    public ExpressionWithFlows translateLiteral(JCTree.JCLiteral literal, IOrigin originOverride,
                                       ExpressionContext context) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> baseGenerator.toOrigin(literal));
        if (literal.getValue() == null) {
            var immutable = context.expectedType() != null && context.expectedType().tsym instanceof Symbol.ClassSymbol classSymbol &&
                    this.baseGenerator.isPure(classSymbol);
            if (immutable) {
                return new ExpressionWithFlows(baseGenerator.expressionCompiler.createCall(origin,
                        new NameSegment(origin, "Null", null), Stream.empty(), context));
            }
        }
        return super.toExprWithFlows(literal, origin, context);
    }

    @Override
    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        if (expr instanceof JCTree.JCLiteral literal) {
            return translateLiteral(literal, originOverride, context);
        }
        if (context.expectedType() != null && expr.type.tsym instanceof Symbol.ClassSymbol valueClass
            && baseGenerator.isPure(valueClass)) {
            var origin = Objects.requireNonNullElseGet(originOverride, () -> baseGenerator.toOrigin(expr));
            var nullableTarget = isNullable(context.expectedType());
            var nullableValue = isNullable(expr.type);
            if (!nullableTarget && nullableValue) {
                var valueExpr = baseGenerator.getFinalGenerator().toExpr(expr, origin, context.withExpectedType(null));
                return new ExpressionWithFlows(new ExprDotName(origin, valueExpr, new Name(origin, "value"), null));
            } else if (nullableTarget && !nullableValue) {
                var nonNullValueExpr = baseGenerator.getFinalGenerator().toExpr(expr, origin, context.withExpectedType(null));
                return new ExpressionWithFlows(ExpressionCompiler.createCall2(origin, new NameSegment(origin, "NonNull", null), Stream.of(nonNullValueExpr)));
            } else {
                return super.toExprWithFlows(expr, originOverride, context);
            }
        }
        return super.toExprWithFlows(expr, originOverride, context);
    }

    @Override
    public List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin originOverride) {
        
        return super.translateStatementAfterLabel(blockCompiler, statement, labels, originOverride);
    }
}
