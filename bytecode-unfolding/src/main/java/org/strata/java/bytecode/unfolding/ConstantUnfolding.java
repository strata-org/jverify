package org.strata.java.bytecode.unfolding;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a program for the bytecode frontend: javac stops folding constant
 * expressions, so the classfile carries the operations the source is written
 * with ({@code 7 / 2} as an {@code idiv}, not {@code iconst_3}), and the
 * frontend, not javac, has to compute them. A TaskListener that rewrites each
 * class after it is attributed and flow-analysed, before it is lowered:
 *
 * <ul>
 *   <li>an operation loses its constant type, so Gen emits it instead of
 *       loading the folded value;</li>
 *   <li>a constant operand of a cast or of a conditional operator is wrapped
 *       in a {@code LetExpr} without definitions, whose value Gen loads onto
 *       the stack: javac would otherwise still fold the conversion
 *       ({@code (int) Double.NaN} to {@code iconst_0}) or the branch, also of
 *       an operation that is not constant ({@code false && f()} to
 *       {@code false});</li>
 *   <li>a final local initialized with a constant stops being a constant
 *       variable, so its uses read the value its initializer computes, not
 *       the one javac folded.</li>
 * </ul>
 *
 * Left constant:
 * <ul>
 *   <li>String-typed expressions (a constant one is interned), case labels,
 *       annotation values, and constant fields (JLS 13.1 inlines their
 *       uses).</li>
 *   <li>A constant local that a local or anonymous class reads: Lower would
 *       capture it in a field, and a condition on it there could no longer
 *       stay folded (below).</li>
 *   <li>An int or long operation on constants that wraps
 *       ({@link #overflows}).</li>
 *   <li>A constant that decides a statement: the condition of a statement or
 *       case guard, down through the {@code &&}, {@code ||}, {@code !} and
 *       {@code ?:} it is built from. javac has pruned the branch it rules
 *       out, and decided reachability and definite assignment on it, so
 *       evaluating it would add a path that cannot run: one that reads a local
 *       before it is assigned, which the JVM verifier rejects, leaves a loop,
 *       which the frontend's loop recovery can get wrong, or reaches code
 *       javac drops.</li>
 *   <li>In an expression that mentions a local declared without an
 *       initializer, a constant operand of {@code &&}, {@code ||} or
 *       {@code !}, the condition of {@code ?:} or a branch of a boolean one, or
 *       a yield of a boolean switch expression: definite assignment may use its
 *       value (JLS 16).</li>
 * </ul>
 *
 * <p>Values are unchanged: javac folds exactly what the JVM computes.
 *
 * <p>It is not a javac plugin: javac loads every plugin on its classpath,
 * which under Brazil holds every jar this package publishes. The JVM needs
 * the exports {@link Exports} checks.
 */
public final class ConstantUnfolding implements TaskListener {
    private final Trees trees;
    private final TreeMaker make;

    private ConstantUnfolding(JavacTask task) {
        this.trees = Trees.instance(task);
        this.make = TreeMaker.instance(((BasicJavacTask) task).getContext());
    }

    /** Add the rewrite to {@code task}, which must not have started analysing. */
    public static void install(JavacTask task) {
        task.addTaskListener(new ConstantUnfolding(task));
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() != TaskEvent.Kind.ANALYZE || e.getTypeElement() == null) {
            return;
        }
        JCTree tree = (JCTree) trees.getTree(e.getTypeElement());
        if (tree == null) {
            return;
        }
        Set<VarSymbol> blankLocals = new HashSet<>();
        Set<VarSymbol> captured = new HashSet<>();
        new TreeScanner() {
            private final Map<VarSymbol, JCClassDecl> declaredIn = new HashMap<>();
            private JCClassDecl enclosing;

            @Override
            public void visitClassDef(JCClassDecl c) {
                JCClassDecl prev = enclosing;
                enclosing = c;
                super.visitClassDef(c);
                enclosing = prev;
            }

            // Each local's class, and the locals that can be unassigned somewhere in their scope.
            // Parameters, catch, for-each and pattern variables are assigned where their scope
            // starts, and are not visited.
            @Override
            public void visitVarDef(JCVariableDecl v) {
                if (v.sym != null && isLocalOwner(v.sym.owner)) {
                    declaredIn.put(v.sym, enclosing);
                    if (v.init == null) blankLocals.add(v.sym);
                }
                super.visitVarDef(v);
            }

            // A local that a local or anonymous class reads from enclosing code.
            @Override
            public void visitIdent(JCIdent id) {
                if (id.sym instanceof VarSymbol v && declaredIn.containsKey(v) && declaredIn.get(v) != enclosing) {
                    captured.add(v);
                }
            }

            @Override
            public void visitMethodDef(JCMethodDecl m) {
                scan(m.body);
            }

            @Override
            public void visitLambda(JCLambda l) {
                scan(l.body);
            }

            @Override
            public void visitCatch(JCCatch c) {
                scan(c.body);
            }

            @Override
            public void visitForeachLoop(JCEnhancedForLoop f) {
                scan(f.expr);
                scan(f.body);
            }

            @Override
            public void visitBindingPattern(JCBindingPattern b) {
            }
        }.scan(tree);
        new Unfolder(blankLocals, captured).scan(tree);
    }

    /** A method, initializer block or field: what owns a local. */
    private static boolean isLocalOwner(Symbol owner) {
        return owner != null && (owner.kind == Kinds.Kind.MTH || owner.kind == Kinds.Kind.VAR);
    }

    private final class Unfolder extends TreeScanner {
        private final Set<VarSymbol> blankLocals;
        private final Set<VarSymbol> captured;
        private final Set<VarSymbol> unconstanted = new HashSet<>();
        private final Map<JCSwitchExpression, Boolean> booleanSwitches = new HashMap<>();
        /**
         * In a conditional nest: whether its constants stay folded, as in a statement's condition, and in an
         * expression where definite assignment may rely on them. Null outside one; a value position inside a
         * nest starts afresh.
         */
        private Boolean nest;

        Unfolder(Set<VarSymbol> blankLocals, Set<VarSymbol> captured) {
            this.blankLocals = blankLocals;
            this.captured = captured;
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            for (JCTree def : tree.defs) {
                if (!(def instanceof JCVariableDecl field && field.sym != null && field.sym.getConstValue() != null)) {
                    scan(def);
                }
            }
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            scan(tree.init);
            VarSymbol v = tree.sym;
            if (v != null && v.getConstValue() != null && !isString(v.type) && !captured.contains(v)) {
                v.setData(null);
                unconstanted.add(v);
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (tree.sym instanceof VarSymbol v && unconstanted.contains(v) && tree.type != null) {
                tree.type = tree.type.baseType();
            }
        }

        @Override
        public void visitAnnotation(JCAnnotation tree) {
        }

        @Override
        public void visitCase(JCCase tree) {
            // Labels stay constant. A rule's body is also in stats (as a yield, for an expression),
            // so, as in TreeScanner, it is not scanned twice.
            condition(tree.guard);
            scan(tree.stats);
        }

        @Override
        public void visitIf(JCIf tree) {
            condition(tree.cond);
            scan(tree.thenpart);
            scan(tree.elsepart);
        }

        @Override
        public void visitWhileLoop(JCWhileLoop tree) {
            condition(tree.cond);
            scan(tree.body);
        }

        @Override
        public void visitDoLoop(JCDoWhileLoop tree) {
            scan(tree.body);
            condition(tree.cond);
        }

        @Override
        public void visitForLoop(JCForLoop tree) {
            scan(tree.init);
            condition(tree.cond);
            scan(tree.step);
            scan(tree.body);
        }

        @Override
        public void visitAssert(JCAssert tree) {
            condition(tree.cond);
            scan(tree.detail);
        }

        @Override
        public void visitBinary(JCBinary tree) {
            if (tree.hasTag(Tag.AND) || tree.hasTag(Tag.OR)) {
                boolean sensitive = enterNest(tree);
                if (!sensitive) strip(tree);
                inNest(sensitive, () -> {
                    condOperand(tree.lhs);
                    condOperand(tree.rhs);
                });
                if (!sensitive) {
                    tree.lhs = load(tree.lhs);
                    tree.rhs = load(tree.rhs);
                }
                return;
            }
            if (!overflows(tree)) {
                strip(tree);
            }
            value(() -> super.visitBinary(tree));
        }

        @Override
        public void visitUnary(JCUnary tree) {
            if (tree.hasTag(Tag.NOT)) {
                boolean sensitive = enterNest(tree);
                if (!sensitive) strip(tree);
                inNest(sensitive, () -> condOperand(tree.arg));
                if (!sensitive) tree.arg = load(tree.arg);
                return;
            }
            if (!overflows(tree)) {
                strip(tree);
            }
            value(() -> super.visitUnary(tree));
        }

        @Override
        public void visitConditional(JCConditional tree) {
            boolean sensitive = enterNest(tree);
            if (!sensitive) strip(tree);
            boolean booleanBranches = tree.type != null && tree.type.hasTag(TypeTag.BOOLEAN);
            inNest(sensitive, () -> condOperand(tree.cond));
            if (!sensitive) tree.cond = load(tree.cond);
            if (booleanBranches) {
                inNest(sensitive, () -> {
                    condOperand(tree.truepart);
                    condOperand(tree.falsepart);
                });
            } else {
                value(() -> {
                    scan(tree.truepart);
                    scan(tree.falsepart);
                });
            }
        }

        @Override
        public void visitSwitchExpression(JCSwitchExpression tree) {
            if (tree.type != null && tree.type.hasTag(TypeTag.BOOLEAN)) {
                booleanSwitches.put(tree, enterNest(tree));
            }
            value(() -> super.visitSwitchExpression(tree));
        }

        @Override
        public void visitYield(JCYield tree) {
            Boolean sensitive = tree.target instanceof JCSwitchExpression sw ? booleanSwitches.get(sw) : null;
            if (sensitive == null) {
                scan(tree.value);
            } else {
                inNest(sensitive, () -> condOperand(tree.value));
            }
        }

        @Override
        public void visitTypeCast(JCTypeCast tree) {
            boolean s = strip(tree);
            value(() -> scan(tree.expr));
            if (s) tree.expr = load(tree.expr);
        }

        @Override
        public void visitParens(JCParens tree) {
            // Transparent for definite assignment (JLS 16): the nest, if any, carries through.
            boolean s = strip(tree);
            if (nest != null && isCondOp(TreeInfo.skipParens(tree.expr))) {
                condOperand(tree.expr);
            } else {
                value(() -> scan(tree.expr));
            }
            if (s) tree.expr = load(tree.expr);
        }

        /** A statement's or case guard's condition. */
        private void condition(JCExpression c) {
            if (c != null) inNest(true, () -> condOperand(c));
        }

        /** An expression in a conditional position of the current nest. */
        private void condOperand(JCExpression e) {
            if (e == null || (nest && isConstant(e))) {
                return; // stays folded
            }
            if (isCondOp(TreeInfo.skipParens(e))) {
                scan(e);
            } else {
                value(() -> scan(e));
            }
        }

        /** Whether the nest that tree belongs to is sensitive; starts one at tree if it is a root. */
        private boolean enterNest(JCExpression tree) {
            return nest != null ? nest : mentionsBlankLocal(tree);
        }

        private void inNest(boolean sensitive, Runnable r) {
            Boolean prev = nest;
            nest = sensitive;
            r.run();
            nest = prev;
        }

        private void value(Runnable r) {
            Boolean prev = nest;
            nest = null;
            r.run();
            nest = prev;
        }

        private boolean mentionsBlankLocal(JCTree t) {
            boolean[] found = {false};
            new TreeScanner() {
                @Override
                public void visitIdent(JCIdent id) {
                    if (id.sym instanceof VarSymbol v && blankLocals.contains(v)) found[0] = true;
                }
            }.scan(t);
            return found[0];
        }

        private boolean strip(JCExpression t) {
            if (isConstant(t) && !isString(t.type)) {
                t.type = t.type.baseType();
                return true;
            }
            return false;
        }

        /** An operand that is still constant, wrapped so that Gen loads it as it is. */
        private JCExpression load(JCExpression e) {
            if (!isConstant(e)) {
                return e;
            }
            LetExpr let = make.at(e.pos).LetExpr(List.nil(), e);
            let.type = e.type.baseType();
            return let;
        }
    }

    private static boolean isCondOp(JCExpression t) {
        return t.hasTag(Tag.AND) || t.hasTag(Tag.OR) || t.hasTag(Tag.NOT) || t.hasTag(Tag.CONDEXPR)
                || (t.hasTag(Tag.SWITCH_EXPRESSION) && t.type != null && t.type.hasTag(TypeTag.BOOLEAN));
    }

    private static boolean isConstant(JCExpression t) {
        return t.type != null && t.type.constValue() != null;
    }

    private static boolean isString(Type t) {
        return t != null && t.hasTag(TypeTag.CLASS);
    }

    /**
     * Whether an int or long operation on constants wraps: its exact value is not
     * the one javac folded. The frontend computes such arithmetic without wrapping
     * and checks a range only where a value is stored, so unfolded it could prove
     * a check that fails on the JVM ({@code 65536 * 65536 != 0}); folded, the
     * value is the JVM's.
     */
    private static boolean overflows(JCExpression tree) {
        if (!isConstant(tree) || !(tree.type.hasTag(TypeTag.INT) || tree.type.hasTag(TypeTag.LONG))) {
            return false;
        }
        BigInteger exact = switch (tree) {
            case JCBinary b -> switch (b.getTag()) {
                case PLUS -> value(b.lhs).add(value(b.rhs));
                case MINUS -> value(b.lhs).subtract(value(b.rhs));
                case MUL -> value(b.lhs).multiply(value(b.rhs));
                case DIV -> value(b.lhs).divide(value(b.rhs));
                // A remainder cannot wrap; the frontend refuses shifts and bitwise operations.
                default -> null;
            };
            case JCUnary u when u.hasTag(Tag.NEG) -> value(u.arg).negate();
            default -> null;
        };
        return exact != null && !exact.equals(value(tree));
    }

    /** An integral constant's value as javac folded it: an Integer, also for a char, or a Long. */
    private static BigInteger value(JCExpression e) {
        return BigInteger.valueOf(((Number) e.type.constValue()).longValue());
    }
}
