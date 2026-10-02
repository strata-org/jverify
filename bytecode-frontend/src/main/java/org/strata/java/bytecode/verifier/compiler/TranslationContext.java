package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.StmtExpr;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the bytecode translator shares with the driver: the compilation
 * services, the per-class policy state, and the {@link MethodScope} of the
 * method being translated. One instance per compilation; {@link #beginMethod}
 * replaces the method scope wholesale, so per-method state cannot leak
 * between methods.
 */
final class TranslationContext {

    final Reporter reporter;
    final SourceMap src;
    final LaurelFactory lf;
    final ClassResolver resolver;
    final CompositeModel composites;
    final MethodGraph graph;

    TranslationContext(Reporter reporter, SourceMap src, LaurelFactory lf,
                       ClassResolver resolver, CompositeModel composites, MethodGraph graph) {
        this.reporter = reporter;
        this.src = src;
        this.lf = lf;
        this.resolver = resolver;
        this.composites = composites;
        this.graph = graph;
    }

    /** The library contracts consulted at call sites; never null ({@link LibraryContracts#none()} by default). */
    LibraryContracts contracts = LibraryContracts.none();
    /** Translating a library contract member rather than a program method. */
    boolean contractMode;
    /**
     * A {@code preconditionOf(call)} awaiting its callee's translation: the
     * placeholder's name -> the callee and the call's receiver/arguments. Resolved
     * once every method is translated (BytecodeToLaurelCompiler.inlinePreconditionOf),
     * because the callee may come later in the compilation.
     */
    record PendingPreconditionOf(String calleeFqn, java.lang.classfile.MethodModel callee,
                                 AstNode<StmtExpr> receiver, List<AstNode<StmtExpr>> args) {
    }
    final Map<String, PendingPreconditionOf> pendingPreconditionOf = new java.util.LinkedHashMap<>();
    final Set<String> constructorCalls = new java.util.HashSet<>();
    final Map<String, org.strata.jverify.laurel.Procedure> missingContractProcs = new java.util.LinkedHashMap<>();

    // --- Per-class state ---

    /** Fqn of the class whose method is being translated: the type of `this`. */
    String currentClassFqn;
    /** Type-parameter names in scope (method + enclosing class): refuse as JFE's Unsupported type: T. */
    Set<String> typeParams = Set.of();
    /**
     * The subset of {@link #typeParams} that become Laurel {@code TVar}s: the
     * METHOD's own type parameters, which the procedure declares in its
     * {@code typeArgs}, plus, in a JArray member, the JArray class's type
     * parameter, which the {@code JArray} composite declares. Any other CLASS
     * type parameter stays refused: its composite is emitted with no
     * {@code typeArgs}, so a {@code TVar} naming it would not resolve. Mirrors
     * JFE's {@code currentMethodTypeParams}.
     */
    Set<String> methodTypeParams = Set.of();

    // --- Per-method state ---

    /** The mutable state of one method's translation. */
    static final class MethodScope {

        /** The method being translated (callee edges are recorded on it). */
        final MethodGraph.MethodInfo info;
        /** Local-name renames in effect (postcondition lambda param -> $result). */
        Map<String, String> renames = Map.of();
        /** Converting a contract argument (precondition/postcondition/invariant/modifies). */
        boolean inContract;
        /**
         * Translating an instance member of the built-in JArray class: its
         * receiver is {@code JArray<TArrayElement>}, its {@code modifies(this)}
         * is {@code self?#contentsVersion}, and {@code old(e)} translates.
         */
        boolean arrayModelMember;
        /** Translating a {@code @Pure} program method: array allocations and writes refuse. */
        boolean pure;
        /** Declared exceptional join class (fqn) from the throws clause, or null. */
        String throwsJoin;
        /** Catch types (fqns) of enclosing try BODIES, innermost first. */
        final Deque<List<String>> enclosingCatchTypes = new ArrayDeque<>();
        /** The current method's class-typed return sort, or null. */
        String returnSort;
        /**
         * This method carries an {@code @Impure} type annotation somewhere, which
         * exempts its reference comparisons from the restricted-{@code ==} check.
         *
         * <p>Method-scoped on purpose, and weaker than JFE's per-operand rule: an
         * annotated cast between types with the SAME erasure
         * ({@code (@Impure Object) b} where {@code b} is already {@code Object})
         * emits no instruction at all, so there is no node to attach the
         * annotation to and no way to tell which operand it qualified. Erring
         * towards silence keeps the check from falsely accusing code JFE accepts
         * (PolymorphismResolutionErrors2); the cost is a missed error in a method
         * that mixes an impure comparison with a value-typed one.
         */
        boolean mentionsImpure;
        boolean pureBody;
        boolean shapeErrorReported;
        /**
         * Translating a relocated source-contract method as its contractee's (see
         * Declarations.sourceContracts). Its body is the contract header followed by
         * {@code throw new ContractException()}, which JFE discards as "contract only"
         * (isContractThrow); no implementation is emitted for it, so that throw is
         * dropped rather than coverage-checked.
         */
        boolean relocatedContract;

        /** Java-label bookkeeping for the loop-label scheme. */
        final Deque<LabelEntry> labelStack = new ArrayDeque<>();
        String pendingLabel;

        /**
         * Float/double comparison results whose relational negation is
         * NaN-unsafe. Identity-based: marks the exact StmtExpr instances the
         * translator built.
         */
        final Set<StmtExpr> unordered = Collections.newSetFromMap(new IdentityHashMap<>());

        /**
         * Nodes that were emitted as STATEMENTS: the rerange stop set, so a
         * wrapping statement's range stamping leaves nested statements'
         * already assigned ranges untouched.
         */
        final Set<AstNode<StmtExpr>> statementNodes = Collections.newSetFromMap(new IdentityHashMap<>());

        MethodScope(MethodGraph.MethodInfo info) {
            this.info = info;
        }
    }

    /** The scope of the method being translated; replaced by {@link #beginMethod}. */
    MethodScope method;

    /** Open a fresh method scope; all per-method state starts from scratch. */
    void beginMethod(MethodGraph.MethodInfo info) {
        method = new MethodScope(info);
    }

    // --- Laurel loop-label scheme (JFE's emitLoop labels) ---

    record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {
    }

    // Label numbering is compilation-scoped (corpus-pinned), so the counters
    // live here, not on the method scope.
    private int labelCounter = 0;
    int switchSelCounter = 0;

    String freshLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    /**
     * Stamp {@code range} over a finished statement's subtree (stopping at
     * nested statements) and record the result as a statement node.
     */
    AstNode<StmtExpr> placeStatement(AstNode<StmtExpr> stmt, FileRange range) {
        AstNode<StmtExpr> placed = LaurelTrees.rerange(stmt, range, method.statementNodes);
        method.statementNodes.add(placed);
        return placed;
    }
}
