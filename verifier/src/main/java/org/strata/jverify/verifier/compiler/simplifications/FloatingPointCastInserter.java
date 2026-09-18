package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

/**
 * Inserts explicit type casts for implicit widening conversions from
 * integral types to floating-point types
 * ({@code int}/{@code long}/{@code short}/{@code byte} → {@code float}/{@code double}).
 * This makes every integer-to-float boundary explicit for downstream
 * translation.
 *
 * <p>The shared JLS §5.6 dispatch lives on {@link CastInserter}; this
 * pass only adds the {@link JCTree.JCBinary} case (mixed integer/float
 * operands need the integer side widened before the operation).
 */
public class FloatingPointCastInserter extends CastInserter {
    public FloatingPointCastInserter(Context context) {
        super(context);
    }

    @Override
    protected boolean shouldCast(Type source, Type target) {
        if (!target.isPrimitive() || !source.isPrimitive()) {
            return false;
        }
        TypeTag sourceTag = source.getTag();
        TypeTag targetTag = target.getTag();
        // int/long/short/byte → double/float
        return (targetTag == TypeTag.DOUBLE || targetTag == TypeTag.FLOAT)
                && isIntegralTag(sourceTag);
    }

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        if (tree.lhs.type != null && tree.rhs.type != null) {
            TypeTag lhsTag = tree.lhs.type.getTag();
            TypeTag rhsTag = tree.rhs.type.getTag();
            boolean lhsIsFloat = lhsTag == TypeTag.DOUBLE || lhsTag == TypeTag.FLOAT;
            boolean rhsIsFloat = rhsTag == TypeTag.DOUBLE || rhsTag == TypeTag.FLOAT;
            if (lhsIsFloat && isIntegralTag(rhsTag)) {
                tree.rhs = insertCastIfNeeded(tree.rhs, tree.lhs.type);
            } else if (rhsIsFloat && isIntegralTag(lhsTag)) {
                tree.lhs = insertCastIfNeeded(tree.lhs, tree.rhs.type);
            }
        }
        result = tree;
    }

    private static boolean isIntegralTag(TypeTag tag) {
        return switch (tag) {
            case INT, LONG, SHORT, BYTE -> true;
            default -> false;
        };
    }
}
