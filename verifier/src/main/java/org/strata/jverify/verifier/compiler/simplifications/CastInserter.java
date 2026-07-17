package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * Shared skeleton for passes that rewrite the Java AST to insert explicit
 * {@link JCTree.JCTypeCast} nodes at JLS §5.6 implicit-conversion sites,
 * so downstream translation only ever has to handle explicit casts.
 *
 * <p>Subclasses supply a single predicate {@link #shouldCast} that decides
 * whether a particular (source, target) pair needs a cast; the shared
 * dispatch here walks the tree and calls it at every JLS conversion
 * boundary (variable initializer, method argument, assignment, compound
 * assignment, return, conditional branches, new-array element).
 *
 * <p>Subclasses can further override {@link #castTargetType} to control
 * the actual target {@link Type} of the emitted cast (e.g. strip
 * JVerify's type-use annotations), and override individual {@code visitX}
 * methods to add pass-specific sites (e.g. {@link JCTree.JCBinary} for
 * floating-point promotion, {@link JCTree.JCLambda} for return-type
 * scoping).
 *
 * <p>The base is deliberately idempotent: if the operand is already an
 * explicit {@code JCTypeCast} to the same target type, no new cast is
 * inserted. This keeps stacked passes from double-wrapping (e.g. running
 * {@link FloatingPointCastInserter} then {@link ImplicitCastInserter}
 * over the same tree).
 */
abstract class CastInserter extends TreeTranslator {
    protected final TreeMaker make;
    protected final Types types;
    protected Type currentMethodReturnType;

    protected CastInserter(Context context) {
        this.make = TreeMaker.instance(context);
        this.types = Types.instance(context);
    }

    /**
     * True iff a cast should be inserted to bridge {@code source} to
     * {@code target}. Called at every JLS §5.6 conversion boundary the
     * base dispatch walks. Both types may be {@code null} — the base
     * catches that before calling this predicate.
     */
    protected abstract boolean shouldCast(Type source, Type target);

    /**
     * The actual {@link Type} used for the emitted cast. Defaults to the
     * conversion's declared target; subclasses may override to strip
     * type-use annotations (JVerify's {@code @Nat}/{@code @Unbounded})
     * that aren't part of the Java conversion.
     */
    protected Type castTargetType(Type target) {
        return target;
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        if (tree.init != null && tree.vartype != null) {
            tree.init = insertCastIfNeeded(tree.init, tree.vartype.type);
        }
        result = tree;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        if (tree.meth.type instanceof MethodType mt) {
            List<Type> params = mt.getParameterTypes();
            ListBuffer<JCTree.JCExpression> newArgs = new ListBuffer<>();
            for (int i = 0; i < tree.args.size(); i++) {
                JCTree.JCExpression arg = tree.args.get(i);
                Type paramType = i < params.size() ? params.get(i) : arg.type;
                newArgs.append(insertCastIfNeeded(arg, paramType));
            }
            tree.args = newArgs.toList();
        }
        result = tree;
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        super.visitAssign(tree);
        tree.rhs = insertCastIfNeeded(tree.rhs, tree.lhs.type);
        result = tree;
    }

    @Override
    public void visitAssignop(JCTree.JCAssignOp tree) {
        super.visitAssignop(tree);
        // `x op= y` implicitly narrows `x op y` back to x's type; wrap
        // the rhs so downstream sees the narrow explicitly.
        tree.rhs = insertCastIfNeeded(tree.rhs, tree.lhs.type);
        result = tree;
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        super.visitReturn(tree);
        if (tree.expr != null && currentMethodReturnType != null) {
            tree.expr = insertCastIfNeeded(tree.expr, currentMethodReturnType);
        }
        result = tree;
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        Type savedReturnType = currentMethodReturnType;
        currentMethodReturnType = tree.restype != null ? tree.restype.type : null;
        super.visitMethodDef(tree);
        currentMethodReturnType = savedReturnType;
    }

    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        super.visitConditional(tree);
        if (tree.type != null) {
            tree.truepart = insertCastIfNeeded(tree.truepart, tree.type);
            tree.falsepart = insertCastIfNeeded(tree.falsepart, tree.type);
        }
        result = tree;
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray tree) {
        super.visitNewArray(tree);
        if (tree.elemtype != null && tree.elems != null) {
            ListBuffer<JCTree.JCExpression> newElems = new ListBuffer<>();
            for (JCTree.JCExpression elem : tree.elems) {
                newElems.append(insertCastIfNeeded(elem, tree.elemtype.type));
            }
            tree.elems = newElems.toList();
        }
        result = tree;
    }

    protected final JCTree.JCExpression insertCastIfNeeded(JCTree.JCExpression expr, Type targetType) {
        if (targetType == null || expr.type == null) {
            return expr;
        }
        // Already an explicit cast to the same target: don't double-wrap.
        if (expr instanceof JCTree.JCTypeCast existing
                && existing.type != null
                && types.isSameType(existing.type, targetType)) {
            return expr;
        }
        if (!shouldCast(expr.type, targetType)) {
            return expr;
        }
        Type castType = castTargetType(targetType);
        JCTree.JCTypeCast cast = make.at(expr.pos).TypeCast(make.Type(castType), expr);
        cast.type = castType;
        return cast;
    }
}
