package org.strata.jverify.verifier.compiler.simplifications;

import org.strata.jverify.EmptyContract;
import org.strata.jverify.Nullable;
import org.strata.jverify.common.Common;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.ArrayDeque;
import java.util.ArrayList;

/*
Extracts contracts from constructor, method or loop bodies
Does not support lambdas.
TODO: could be slightly simplified by compiling DoWhile loops first
 */
public class MethodOrLoopContractCompiler extends TreeTranslator {
    private final TreeMaker maker;
    private final Reporter reporter;
    private final JVerifyUtils jverifyUtils;

    private MethodOrLoopContractCompiler(Context context) {
        context.put(MethodOrLoopContractCompiler.class, this);
        this.maker = TreeMaker.instance(context);
        this.reporter = Reporter.instance(context);
        this.jverifyUtils = JVerifyUtils.instance(context);
    }

    public static MethodOrLoopContractCompiler instance(Context context) {
        MethodOrLoopContractCompiler instance = context.get(MethodOrLoopContractCompiler.class);
        if (instance == null) {
            instance = new MethodOrLoopContractCompiler(context);
        }
        return instance;
    }

    public static List<JCTree.JCStatement> getImplementationStatements(JCTree.JCStatement outerBlock) {
        var implementationBlock = getImplementationBlock(outerBlock);
        return implementationBlock == null ? List.nil() : implementationBlock.getStatements();
    }

    public static JCTree.@Nullable JCBlock getImplementationBlock(JCTree.JCStatement outerBlock) {
        List<JCTree.JCStatement> outerStatements = ((JCTree.JCBlock) outerBlock).getStatements();
        if (outerStatements.size() < 2) {
            return null;
        }
        JCTree.JCStatement second = outerStatements.get(1);
        if (second instanceof JCTree.JCBlock block) {
            return block;
        }
        return null;
    }

    /**
     * Whether {@code method}'s body is [contract, implementation] -- the shape this
     * pass produces.
     *
     * <p>Delegates to {@link #getImplementationBlock} instead of repeating
     * `statements[1] instanceof JCBlock`, which indexed 1 unguarded: on a body of fewer
     * than two statements {@code com.sun.tools.javac.util.List.get} threw
     * {@code IndexOutOfBoundsException} out of a boolean-returning predicate. Only an
     * undeclared pass-ordering invariant kept it from firing -- the sole caller runs
     * after every body is rewritten to exactly [contract, implementation]. A body with
     * no implementation block now yields {@code false}, which the caller's guard
     * already treats as "do not register".
     */
    public static boolean hasImplementation(JCTree.JCMethodDecl method) {
        return method.body != null && getImplementationBlock(method.body) != null;
    }

    public java.util.List<JCTree.JCCompilationUnit> transform(java.util.List<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }

    public void removeImplementation(JCTree.JCMethodDecl tree) {
        if (tree.body == null || tree.body.getStatements().isEmpty()) {
            return;
        }
        var contractBlock = MethodOrLoopContractCompiler.getContractBlock(tree.body);
        tree.body.stats = List.of(contractBlock, jverifyUtils.contractThrow());
    }
    
    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        reporter.compilationUnit = tree;
        super.visitTopLevel(tree);
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop loop) {
        if (loop.body != null) {
            visitLoop(loop, new Property<JCTree.JCStatement>() {
                @Override
                public JCTree.JCStatement get() {
                    return loop.body;
                }

                @Override
                public void set(JCTree.JCStatement value) {
                    loop.body = value;
                }
            });
        }
        super.visitDoLoop(loop);
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop loop) {
        if (loop.body != null) {
            visitLoop(loop, new Property<JCTree.JCStatement>() {
                @Override
                public JCTree.JCStatement get() {
                    return loop.body;
                }

                @Override
                public void set(JCTree.JCStatement value) {
                    loop.body = value;
                }
            });
        }
        super.visitWhileLoop(loop);
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop loop) {
        if (loop.body != null) {
            visitLoop(loop, new Property<JCTree.JCStatement>() {
                @Override
                public JCTree.JCStatement get() {
                    return loop.body;
                }

                @Override
                public void set(JCTree.JCStatement value) {
                    loop.body = value;
                }
            });
        }
        super.visitForLoop(loop);
    }

    private void visitLoop(JCTree loop, Property<JCTree.JCStatement> loopBody) {
        maker.pos = loopBody.get().pos;
        List<JCTree.JCStatement> newStatements = getNewStatements(loop, getStatements(loopBody.get()), false);
        loopBody.set(maker.Block(0, newStatements));
        var contract = getContract(loopBody.get(), false);
        checkEmptyExpressions(loop, contract.preconditions(), "preconditions", "loop");
        checkEmptyExpressions(loop, contract.postconditions(), "postconditions", "loop");
        checkEmptyExpressions(loop,
                contract.throwsOnCases().stream()
                        .map(MethodOrLoopContract.ThrowsOnCase::guard).toList(),
                "throwsOn cases", "loop");
    }

    List<JCTree.JCStatement> getStatements(JCTree.JCStatement statement) {
        return statement instanceof JCTree.JCBlock block ? block.getStatements() : List.of(statement);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        if (tree.body != null) {
            var allowFooter = JVerifyUtils.isConstructor(tree.sym);
            tree.body.stats = getNewStatements(tree, tree.body.getStatements(), allowFooter);
            var contract = getContract(tree.body, false);
            checkEmptyExpressions(tree, contract.loopInvariants(), "invariants", "method");
        } if (tree.sym.getAnnotation(EmptyContract.class) != null) {
            tree.body = maker.Block(0, getNewStatements(tree, List.nil(), false));        
        }
        super.visitMethodDef(tree);
    }

    private List<JCTree.JCStatement> getNewStatements(JCTree tree, List<JCTree.JCStatement> statements, boolean allowFooter) {
        var superOrThis = getSuperOrThis(statements);

        if (superOrThis != null) {
            statements = statements.tail;
        }

        java.util.List<JCTree.JCStatement> remainingStatements = new ArrayList<>(statements);
        var contractStatements = new ArrayList<JCTree.JCStatement>();
        remainingStatements = addHeaderContracts(remainingStatements, contractStatements);

        if (allowFooter) {
            remainingStatements = addFooterContracts(remainingStatements, contractStatements);
        }

        maker.pos = tree.pos;

        if (superOrThis != null) {
            remainingStatements = new ArrayList<>(remainingStatements);
            remainingStatements.addFirst(superOrThis);
        }
        return getOuterBlockStatements(contractStatements, remainingStatements);
    }

    public List<JCTree.JCStatement> getOuterBlockStatements(java.util.List<JCTree.JCStatement> contractStatements, java.util.List<JCTree.JCStatement> remainingStatements) {
        var contractBlock = maker.Block(0, List.from(contractStatements));
        JCTree.JCBlock implementationBlock = maker.Block(0, List.from(remainingStatements));
        return List.<JCTree.JCStatement>of(implementationBlock).prepend(contractBlock);
    }

    private java.util.List<JCTree.JCStatement> addFooterContracts(java.util.List<JCTree.JCStatement> remainingStatements, ArrayList<JCTree.JCStatement> contractStatements) {
        int i;
        for (i = remainingStatements.size() - 1; i > 0; i--) {
            var current = remainingStatements.get(i);
            boolean foundHeader = isContractStatement(current);
            if (!foundHeader) {
                break;
            }
            contractStatements.add(current);
        }
        int footerContracts = i + 1;
        remainingStatements = remainingStatements.subList(0, footerContracts);
        return remainingStatements;
    }

    private static JCTree.JCStatement getSuperOrThis(List<JCTree.JCStatement> statements) {
        JCTree.JCStatement superOrThis = null;
        var first = statements.isEmpty() ? null : statements.getFirst();
        if ((first instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            var isSuperOrThisCall = invocation.getMethodSelect() instanceof JCTree.JCIdent ident &&
                    (ident.name == ident.name.table.names._super || ident.name == ident.name.table.names._this);
            if (isSuperOrThisCall) {
                superOrThis = first;
            }
        }
        return superOrThis;
    }

    private java.util.List<JCTree.JCStatement> addHeaderContracts(
            java.util.List<JCTree.JCStatement> statements,
            java.util.List<JCTree.JCStatement> headerContracts) {
        int i;
        for (i = 0; i < statements.size(); i++) {
            var statement = statements.get(i);
            boolean isContract = isContractStatement(statement);
            if (!isContract) {
                break;
            }
            headerContracts.add(statement);
        }
        return statements.subList(headerContracts.size(), statements.size());
    }

    private boolean isContractStatement(JCTree.JCStatement statement) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return false;
        }

        var methodName = jverifyMethod.getQualifiedName().toString();
        switch (methodName) {
            case "check", "assume" -> {
                // not a header method, so stop here
                return false;
            }
            case Common.PRECONDITION, "postcondition", "invariant", "decreases", "reads", "modifies",
                 "throwsOn" -> {
                return true;
            }
            default -> {
                reporter.reportError(invocation, "notSupported", methodName);
                return false;
            }
        }
    }

    public JCTree.JCBlock getDefaultBody() {
        JCTree.JCStatement implementation = jverifyUtils.contractThrow();
        return maker.Block(0, List.of(
                maker.Block(0, List.nil()),
                implementation));
    }

    public static JCTree.JCBlock getContractBlock(JCTree.JCStatement outerBlock) {
        if (!hasContractStructure(outerBlock)) {
            throw new JavaViolationException(
                    "Expected a contract-bearing block (a JCBlock whose first statement is a JCBlock), but got: "
                            + (outerBlock == null ? "null" : outerBlock.getClass().getSimpleName()));
        }
        return (JCTree.JCBlock) ((JCTree.JCBlock) outerBlock).getStatements().get(0);
    }

    /// True when `outerBlock` has the contract-bearing shape this
    /// compiler produces: a JCBlock whose first statement is itself
    /// a JCBlock (holding the precondition/postcondition/invariant
    /// calls). Loop bodies authored without explicit invariants —
    /// or javac-synthesized loop bodies (e.g. enhanced-for
    /// desugared to while+iterator) — do not have this shape, so
    /// callers should guard with this predicate before calling
    /// `getContract` / `getContractBlock`, which otherwise throw
    /// `JavaViolationException`.
    public static boolean hasContractStructure(JCTree.JCStatement outerBlock) {
        if (!(outerBlock instanceof JCTree.JCBlock blk)) {
            return false;
        }
        var stmts = blk.getStatements();
        return !stmts.isEmpty() && stmts.get(0) instanceof JCTree.JCBlock;
    }

    /**
     * The base {@code throwsOn(...)} call when {@code invocation} is a chained
     * exceptional-behavior case
     * ({@code throwsOn(C).postcondition(...).modifies(...)}), or {@code null}
     * when it is not. Clause links are appended to {@code links} in source
     * order when it is. Grouping is structural: a case is one statement, its
     * clauses chain off the {@code ThrowsOnCase} handle {@code throwsOn}
     * returns, and a clause without its case does not compile.
     */
    private static JCTree.JCMethodInvocation throwsOnChainBase(
            JCTree.JCMethodInvocation invocation,
            java.util.List<JCTree.JCMethodInvocation> links) {
        var stack = new ArrayDeque<JCTree.JCMethodInvocation>();
        var cur = invocation;
        // Descend through the receiver chain; skipParens keeps a
        // parenthesized link -- `(throwsOn(C)).postcondition(...)` -- one
        // chain rather than a "detached handle" refusal.
        while (cur.meth instanceof JCTree.JCFieldAccess fa
                && TreeInfo.skipParens(fa.selected) instanceof JCTree.JCMethodInvocation inner) {
            stack.push(cur);
            cur = inner;
        }
        var base = JVerifyUtils.getJVerifyMethod(cur);
        if (base == null || !base.getQualifiedName().contentEquals("throwsOn")) {
            return null;
        }
        while (!stack.isEmpty()) {
            links.add(stack.pop());
        }
        return cur;
    }

    public MethodOrLoopContract getContract(JCTree.JCStatement outerBlock) {
        return getContract(outerBlock, true);
    }

    /**
     * Collect the contract. {@code rejectDetachedCaseClauses} guards the
     * captured/detached-ThrowsOnCase refusals: the translation phase passes
     * {@code true} (a {@link JavaViolationException} there is a per-method
     * skip with a diagnostic), while the earlier tree-rewriting visitors pass
     * {@code false} -- an exception thrown inside a javac visitor would abort
     * the whole compilation rather than skip the method.
     */
    public MethodOrLoopContract getContract(JCTree.JCStatement outerBlock,
                                            boolean rejectDetachedCaseClauses) {
        var contractBlock = getContractBlock(outerBlock);
        ArrayList<Property<JCTree.JCExpression>> precondition = new ArrayList<>();
        ArrayList<Property<JCTree.JCExpression>> postcondition = new ArrayList<>();
        ArrayList<JCTree.JCExpression> decreases = new ArrayList<>();
        ArrayList<Property<JCTree.JCExpression>> loopInvariant = new ArrayList<>();
        ArrayList<Property<JCTree.JCExpression>> reads = new ArrayList<>();
        ArrayList<Property<JCTree.JCExpression>> modifies = new ArrayList<>();
        ArrayList<MethodOrLoopContract.ThrowsOnCase> throwsOnCases = new ArrayList<>();

        for (var contractStatement : contractBlock.getStatements()) {
            // A captured case handle -- `ThrowsOnCase c = throwsOn(...)` --
            // never reaches this loop: a variable declaration ends the
            // contract block, so the declaration (and everything after it)
            // converts as body statements, where the `throwsOn` call is
            // refused as an unsupported JVerify method. The check below covers
            // the other detachment shape: a clause called on a handle that is
            // in scope without a chain (e.g. a field).
            if (!((JCTree.JCStatement) contractStatement instanceof JCTree.JCExpressionStatement expressionStatement
                    && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
                continue;
            }
            // An exceptional behavior case is a call chain rooted at
            // `throwsOn`; recognize it before the flat-clause dispatch below,
            // whose method-name switch would otherwise mistake the chain's
            // outermost `.postcondition(...)`/`.modifies(...)` link for the
            // normal-exit clause of the same name.
            ArrayList<JCTree.JCMethodInvocation> caseLinks = new ArrayList<>();
            var caseBase = throwsOnChainBase(invocation, caseLinks);
            if (caseBase != null) {
                if (caseBase.args.size() != 1) {
                    throw new JavaViolationException("A throwsOn call must have exactly one argument");
                }
                ArrayList<Property<JCTree.JCExpression>> casePostconditions = new ArrayList<>();
                ArrayList<Property<JCTree.JCExpression>> caseModifies = new ArrayList<>();
                for (var link : caseLinks) {
                    var linkName = ((JCTree.JCFieldAccess) link.meth).name.toString();
                    if (link.args.size() != 1) {
                        throw new JavaViolationException(
                                "A throwsOn case's " + linkName + " clause must have exactly one argument");
                    }
                    switch (linkName) {
                        case "postcondition" ->
                            casePostconditions.add(Property.fromElement(link.getArguments(), 0));
                        case "modifies" ->
                            caseModifies.add(Property.fromElement(link.getArguments(), 0));
                        default ->
                            // Drift guard: a new clause method added to
                            // `ThrowsOnCase` without a parser arm should fail
                            // loudly. (Inherited Object methods in a chain
                            // never reach this switch -- a statement whose
                            // outermost call is not JVerify-owned ends the
                            // contract header and converts as body, where the
                            // inner `throwsOn` is refused.)
                            throw new JavaViolationException(
                                    "Unsupported throwsOn case clause: " + linkName);
                    }
                }
                throwsOnCases.add(new MethodOrLoopContract.ThrowsOnCase(
                        Property.fromElement(caseBase.getArguments(), 0),
                        casePostconditions, caseModifies));
                continue;
            }
            var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
            if (jverifyMethod == null) {
                continue;
            }
            // `ThrowsOnCase` is nested in JVerify, so its clause methods
            // resolve as JVerify-owned; reaching one here (rather than through
            // `throwsOnChainBase` above) means the receiver is a captured
            // handle, not a chain -- the clause has been detached from its
            // case. Refuse rather than misfile it as the normal-exit clause of
            // the same name.
            if (rejectDetachedCaseClauses
                    && jverifyMethod.owner.getSimpleName().contentEquals("ThrowsOnCase")) {
                throw new JavaViolationException(
                        "a throwsOn case clause must chain directly off throwsOn(...);"
                                + " calling '" + jverifyMethod.getQualifiedName()
                                + "' on a captured handle detaches it from its case");
            }
            var methodName = jverifyMethod.getQualifiedName().toString();
            switch (methodName) {
                case "check", "assume" -> {
                    // not a header method, so stop here
                    continue;
                }
                case Common.PRECONDITION -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A call to 'precondition' may have only one argument");
                    }
                    precondition.add(Property.fromElement(invocation.getArguments(), 0));
                }
                case "postcondition" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A postcondition call may have only one argument");
                    }
                    postcondition.add(Property.fromElement(invocation.getArguments(), 0));
                }
                case "invariant" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("invariant should have a single argument");
                    }
                    loopInvariant.add(Property.fromElement(invocation.getArguments(), 0));
                }
                case "decreases" -> {
                    for (var decrease : invocation.getArguments()) {
                        // The LOWER javac phase inserts an explicit NewArray for varargs
                        if (decrease instanceof JCTree.JCNewArray newArray) {
                            decreases.addAll(newArray.getInitializers());
                        } else {
                            decreases.add(decrease);
                        }
                    }
                }
                case "reads" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A reads call must have exactly one argument");
                    }
                    reads.add(Property.fromElement(invocation.getArguments(), 0));
                }
                case "modifies" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A modifies call must have exactly one argument");
                    }
                    modifies.add(Property.fromElement(invocation.getArguments(), 0));
                }
                default -> {
                    reporter.reportError(invocation, "notSupported", methodName);
                }
            }
        }
        return new MethodOrLoopContract(precondition, postcondition,
                loopInvariant, decreases, reads, modifies, throwsOnCases);
    }

    public void checkEmptyExpressions(JCTree tree,
                                      java.util.List<Property<JCTree.JCExpression>> expressions,
                                      String typeName,
                                      String containerName) {
        if (!expressions.isEmpty()) {
            reporter.reportError(tree, "wrongContract", typeName, containerName);
        }
    }
}