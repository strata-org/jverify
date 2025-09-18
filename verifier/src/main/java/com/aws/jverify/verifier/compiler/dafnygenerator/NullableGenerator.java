package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.Reporter;
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

    private boolean isNullable(com.sun.tools.javac.code.Type type, JCTree.JCModifiers additionalModifiers) {
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

    private boolean isNullable(JCTree.JCModifiers modifiers) {
        return BaseDafnyGenerator.isAnnotated(modifiers, com.aws.jverify.Nullable.class);
    }

    private boolean isNullable(com.sun.tools.javac.code.Type type) {
        return BaseDafnyGenerator.isAnnotated(type, com.aws.jverify.Nullable.class);
    }

    @Override
    public Expression translateLiteral(JCTree.JCExpression expr, JCTree.JCLiteral literal, IOrigin origin) {
        var immutable = expr.type.tsym instanceof Symbol.ClassSymbol classSymbol &&
                this.baseGenerator.isImmutable(classSymbol);
        if (immutable && literal.getValue() == null) {
            var type = baseGenerator.translateType(expr.type, origin);
            return baseGenerator.expressionCompiler.createCall(origin, 
                    new NameSegment(origin, "Null", List.of(type)), Stream.empty());
        }
        return super.translateLiteral(expr, literal, origin);
    }
    
    
}
