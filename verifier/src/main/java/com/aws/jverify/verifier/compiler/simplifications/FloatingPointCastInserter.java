package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * Inserts explicit type casts for implicit widening conversions from integral types
 * to floating point types (int/long/short/byte -> double/float).
 * This simplifies downstream Dafny translation by making all conversions explicit.
 */
public class FloatingPointCastInserter extends TreeTranslator {
    private final TreeMaker make;
    private Type currentMethodReturnType;

    public FloatingPointCastInserter(Context context) {
        this.make = TreeMaker.instance(context);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
        if (tree.init != null) {
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

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        // Handle mixed double/int operations (arithmetic and comparisons)
        if (tree.lhs.type != null && tree.rhs.type != null) {
            TypeTag lhsTag = tree.lhs.type.getTag();
            TypeTag rhsTag = tree.rhs.type.getTag();

            // Only promote for double (not float, as float is not supported)
            if (lhsTag == TypeTag.DOUBLE && isIntegralTag(rhsTag)) {
                tree.rhs = insertCastIfNeeded(tree.rhs, tree.lhs.type);
            } else if (rhsTag == TypeTag.DOUBLE && isIntegralTag(lhsTag)) {
                tree.lhs = insertCastIfNeeded(tree.lhs, tree.rhs.type);
            }
        }
        result = tree;
    }

    private boolean isIntegralTag(TypeTag tag) {
        return switch (tag) {
            case INT, LONG, SHORT, BYTE -> true;
            default -> false;
        };
    }

    private JCTree.JCExpression insertCastIfNeeded(JCTree.JCExpression expr, Type targetType) {
        if (targetType == null || expr.type == null) {
            return expr;
        }

        if (needsFloatingPointPromotion(expr.type, targetType)) {
            JCTree.JCTypeCast cast = make.at(expr.pos).TypeCast(make.Type(targetType), expr);
            cast.type = targetType;
            return cast;
        }
        return expr;
    }

    private boolean needsFloatingPointPromotion(Type source, Type target) {
        if (!target.isPrimitive() || !source.isPrimitive()) {
            return false;
        }

        TypeTag sourceTag = source.getTag();
        TypeTag targetTag = target.getTag();

        // Only handle int/long/short/byte -> double conversions (not float, as float is not supported)
        return targetTag == TypeTag.DOUBLE && isIntegralTag(sourceTag);
    }
}
