package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
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
 * <p>Reference upcasts are also implicit in Java, but the JVerify Laurel
 * encoding does not currently model the Java type hierarchy (each opaque
 * composite is declared without an {@code extends} clause), so an
 * inserted upcast would be rejected by Laurel's resolver. Adding
 * hierarchy modeling is a separate task.
 */
public class ImplicitCastInserter extends CastInserter {
    public ImplicitCastInserter(Context context) {
        super(context);
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
}
