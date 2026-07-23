package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

/**
 * Inserts explicit type casts at implicit primitive-conversion sites so
 * downstream translation sees a {@link JCTree.JCTypeCast} at every JLS
 * §5.6 boundary. Complements {@link FloatingPointCastInserter}, which
 * handles the integer→float subset; this pass is a strict superset of
 * that logic for integral types, and runs after it so its casts are
 * visible and idempotently skipped.
 *
 * <p>Also applies JLS §5.6.2 binary numeric promotion to mixed-width
 * integral operands of arithmetic, comparison, equality, and bitwise
 * operators. When the two operands have different integer widths — e.g.
 * {@code long * int} or {@code byte == long} — the narrower operand is
 * widened so downstream lowering emits a single-width operator. Without
 * this, the Laurel output has mismatched operand types (e.g.
 * {@code mul(int64, int32)}), which does not match JLS-mandated operand
 * types and can trip Strata's type checker.
 *
 * <p>Reference upcasts are also implicit in Java, but the JVerify Laurel
 * encoding does not currently model the Java type hierarchy (each opaque
 * composite is declared without an {@code extends} clause), so an
 * inserted upcast would be rejected by Laurel's resolver. Adding
 * hierarchy modeling is a separate task.
 */
public class ImplicitCastInserter extends CastInserter {
    private final Symtab symtab;

    public ImplicitCastInserter(Context context) {
        super(context);
        this.symtab = Symtab.instance(context);
    }

    @Override
    protected boolean shouldCast(Type source, Type target) {
        // Primitive-to-primitive with different tags.
        return source.isPrimitive() && target.isPrimitive()
                && source.getTag() != target.getTag();
    }

    /**
     * Cast to the unannotated primitive type. Type-use annotations like
     * {@code @Nat}/{@code @Unbounded} are JVerify's, not Java's, and don't
     * participate in Java's implicit widening/narrowing. The outer
     * statement (variable init / assignment / return) already carries the
     * annotated type on its target, so its own boundary constraint check
     * covers the annotation; making the inserted cast carry the
     * annotation too would produce a spurious second check at the
     * operand's position.
     */
    @Override
    protected Type castTargetType(Type target) {
        return target.baseType();
    }

    /**
     * Lambda bodies have their own return type; save/restore
     * {@code currentMethodReturnType} around the lambda so a {@code return}
     * inside a block lambda gets cast to the lambda's descriptor return
     * type, not the enclosing method's.
     *
     * <p>Only needed here (not in {@link FloatingPointCastInserter})
     * because {@code ImplicitCastInserter} is the only pass that fires
     * before {@code unlambda} lowers lambdas away.
     */
    @Override
    public void visitLambda(JCTree.JCLambda tree) {
        Type savedReturnType = currentMethodReturnType;
        currentMethodReturnType = tree.getDescriptorType(types).getReturnType();
        super.visitLambda(tree);
        currentMethodReturnType = savedReturnType;
    }

    /**
     * JLS §5.6.2 binary numeric promotion for mixed integral operands.
     * {@link FloatingPointCastInserter} already widens integer→float; this
     * covers the pure-integer subset: for {@code + - * / %},
     * {@code < <= > >=}, {@code == !=}, {@code & | ^} where the two
     * operands have <em>different</em> integer widths, widen the narrower
     * operand to the wider one so Strata sees a single operator width.
     *
     * <p>Only mixed-width pairs get a cast. Same-width pairs
     * ({@code byte + byte}, {@code int + int}, …) are left alone: the JLS
     * would still promote a narrower type up to {@code int}, but Strata
     * only cares that both operands share a Laurel width. Emitting a
     * widening helper call on both sides of {@code byte + byte} would add
     * two trivial cast obligations and lose the {@code int8} value's
     * range information for no downstream benefit.
     *
     * <p>Shift operators ({@code << >> >>>}) use JLS §5.6.1 unary numeric
     * promotion on each operand independently — the RHS is NOT promoted
     * to the LHS type — so they are excluded here.
     *
     * <p>Boolean {@code == != & | ^} on {@code boolean} operands do not
     * trigger numeric promotion and are excluded via
     * {@link #isIntegralTag(TypeTag)}.
     *
     * <p>Operands carrying JVerify's {@code @Unbounded} annotation map to
     * Laurel's raw (mathematically unbounded) int, not int32/int64.
     * Promoting through the bounded cast helper would drop that
     * "unbounded" fact and turn a fresh proof obligation onto the target's
     * range, so those operands are also skipped.
     */
    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        if (!isNumericPromotingOp(tree.getTag())
                || tree.lhs.type == null || tree.rhs.type == null) {
            result = tree;
            return;
        }
        TypeTag lhsTag = tree.lhs.type.getTag();
        TypeTag rhsTag = tree.rhs.type.getTag();
        if (!isIntegralTag(lhsTag) || !isIntegralTag(rhsTag)
                || lhsTag == rhsTag) {
            result = tree;
            return;
        }
        if (JVerifyUtils.isAnnotated(tree.lhs.type, org.strata.jverify.Unbounded.class)
                || JVerifyUtils.isAnnotated(tree.rhs.type, org.strata.jverify.Unbounded.class)) {
            result = tree;
            return;
        }
        Type promoted = promotedIntegralType(lhsTag, rhsTag);
        tree.lhs = insertCastIfNeeded(tree.lhs, promoted);
        tree.rhs = insertCastIfNeeded(tree.rhs, promoted);
        result = tree;
    }

    private static boolean isNumericPromotingOp(JCTree.Tag tag) {
        return switch (tag) {
            case PLUS, MINUS, MUL, DIV, MOD,
                 LT, LE, GT, GE, EQ, NE,
                 BITAND, BITOR, BITXOR -> true;
            default -> false;
        };
    }

    private static boolean isIntegralTag(TypeTag tag) {
        return switch (tag) {
            case BYTE, SHORT, CHAR, INT, LONG -> true;
            default -> false;
        };
    }

    /**
     * The type both operands should be promoted to when their JLS types
     * differ. Rule is that if either operand is of type long, the promotion
     * goes to long, and to int otherwise.
     */
    private Type promotedIntegralType(TypeTag lhs, TypeTag rhs) {
        if (lhs == TypeTag.LONG || rhs == TypeTag.LONG) {
            return symtab.longType;
        }
        return symtab.intType;
    }
}
