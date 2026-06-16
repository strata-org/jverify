package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.*;
import org.strata.jverify.verifier.VerifierOptions;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.frontend.JavaLowerer;
import org.strata.jverify.verifier.compiler.frontend.JVerifyIndex;
import org.strata.jverify.verifier.compiler.simplifications.JVerifyUtils;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import org.strata.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import org.strata.jverify.verifier.laurel.FilesMap;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.*;

import static org.strata.jverify.laurel.Laurel.*;

public class JavaToLaurelCompiler {
    /// The name Laurel binds a procedure's return value to inside ensures
    /// clauses. A 1-parameter postcondition lambda's parameter is renamed
    /// to this so the clause refers to the return value correctly.
    private static final String LAUREL_RESULT_BINDING = "result";
    /// javac's synthetic source path for trees injected by a
    /// simplification pass; Strata reports diagnostics for them against
    /// this URI, which has no entry in our line map.
    private static final String SYNTHETIC_UNKNOWN_PATH = "/<unknown>";

    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    private final Reporter reporter;
    private final VerifyAnnotationCompiler annotationCompiler;
    private final JVerifyIndex index;
    JCTree.JCCompilationUnit currentCompilationUnit;

    /// Names of class/record/sealed types referenced as opaque Laurel
    /// CompositeType sorts during translation. Each must be declared with
    /// a compositeCommand so Strata's resolver can find the sort; insertion
    /// order is preserved for deterministic output.
    private final Set<String> referencedCompositeTypes = new LinkedHashSet<>();

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        annotationCompiler = VerifyAnnotationCompiler.instance(context);
        index = JVerifyIndex.instance(context);
    }

    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();

        // Pass 1: translate every compilation unit, collecting candidate
        // procedures (with their referenced user-method callees) per unit.
        // Emission is deferred to Pass 2 so the transitive-emittability fixpoint
        // can run across the whole program before anything is written.
        var unitsInOrder = new ArrayList<JCTree.JCCompilationUnit>();
        var proceduresPerUnit = new HashMap<JCTree.JCCompilationUnit, List<EmittedProcedure>>();
        for (var compilationUnit : loweredResult.parsed()) {
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            currentCompilationUnit = compilationUnit;
            reporter.compilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            unitsInOrder.add(compilationUnit);
            proceduresPerUnit.put(compilationUnit, visitor.procedures);
            lineMaps.put(compilationUnit.sourcefile.toUri(), compilationUnit.getLineMap());
        }

        // Drop methods whose mangled name collides with another's (the flat
        // Class_method scheme can't disambiguate some nested/cross-package names):
        // emitting both gives a duplicate Laurel symbol Strata rejects, so skip
        // the whole group. Before the fixpoint, so callers cascade-drop.
        var byMangledName = new HashMap<String, List<EmittedProcedure>>();
        for (var procs : proceduresPerUnit.values()) {
            for (var ep : procs) {
                byMangledName.computeIfAbsent(ep.mangledName(), k -> new ArrayList<>()).add(ep);
            }
        }
        var collisions = new HashSet<EmittedProcedure>();
        for (var group : byMangledName.values()) {
            if (group.size() > 1) {
                for (var ep : group) {
                    collisions.add(ep);
                    if (isStatic(ep.methodDecl())) {
                        reporter.compilationUnit = ep.compilationUnit();
                        reporter.reportError(ep.methodDecl(), "translatorError",
                                "method name '" + ep.mangledName()
                                        + "' collides with another method after name mangling");
                    }
                    annotationCompiler.markSkipped(ep.compilationUnit(), ep.methodDecl());
                }
            }
        }
        for (var procs : proceduresPerUnit.values()) {
            procs.removeAll(collisions);
        }

        // Transitive-emittability fixpoint, keyed on method-symbol identity (not
        // the mangled name, so collisions handled above don't alias here): a
        // procedure can only be emitted if every user method it calls is also
        // emitted. Dropping one can undefine its callers, so iterate to a fixpoint.
        var emittedSymbols = new HashSet<Symbol.MethodSymbol>();
        for (var procs : proceduresPerUnit.values()) {
            for (var ep : procs) {
                emittedSymbols.add(ep.methodDecl().sym);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var procs : proceduresPerUnit.values()) {
                var it = procs.iterator();
                while (it.hasNext()) {
                    var ep = it.next();
                    boolean hasMissingCallee = ep.referencedCallees().stream()
                            .anyMatch(callee -> !emittedSymbols.contains(callee));
                    if (hasMissingCallee) {
                        it.remove();
                        emittedSymbols.remove(ep.methodDecl().sym);
                        if (isStatic(ep.methodDecl())) {
                            reporter.compilationUnit = ep.compilationUnit();
                            reporter.reportError(ep.methodDecl(), "translatorError",
                                    "call to a method that could not be translated");
                        }
                        annotationCompiler.markSkipped(ep.compilationUnit(), ep.methodDecl());
                        changed = true;
                    }
                }
            }
        }

        // Pass 2: emit the surviving procedures, declaring each referenced
        // opaque composite sort before the procedures that use it.
        boolean first = true;
        Set<String> emittedCompositeTypes = new HashSet<>();
        for (var compilationUnit : unitsInOrder) {
            currentCompilationUnit = compilationUnit;
            reporter.compilationUnit = compilationUnit;
            List<Command> commands = new ArrayList<>();
            if (first) {
                commands.addAll(getPredefinedTypes());
                first = false;
            }
            // Declare each opaque composite sort referenced so far that has
            // not been declared yet, before the procedures that use it, so
            // Strata's resolver can find the sort.
            for (var typeName : referencedCompositeTypes) {
                if (emittedCompositeTypes.add(typeName)) {
                    commands.add(compositeCommand(
                            composite(typeName, Optional.empty(), List.of(), List.of())));
                }
            }
            for (var ep : proceduresPerUnit.get(compilationUnit)) {
                commands.add(procedureCommand(ep.procedure()));
            }
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(), commands));
        }

        FilesMap filesMap = (uri, offset) -> {
            var lineMap = lineMaps.get(uri);
            if (lineMap == null) {
                // jverify threads a source range from the generating Java
                // tree into every synthesized clause/binding (see the
                // requires/ensures/invariant construction above), so a
                // diagnostic should normally carry a real user-source
                // location. If one still arrives against Strata's synthetic
                // "<unknown>" path, some synthesis site failed to thread its
                // source -- a jverify bug. Surface it LOUDLY (so it is not
                // hidden behind a misleading location) but do not crash: a
                // 1:1 fallback keeps the underlying Strata diagnostic visible
                // to the user instead of masking it with an exception.
                //
                // Any OTHER unmapped URI indicates a real line-map collection
                // bug; keep failing fast there.
                String path = uri.getPath();
                if (path != null && path.endsWith(SYNTHETIC_UNKNOWN_PATH)) {
                    System.err.println("[jverify] internal: a Strata diagnostic was reported "
                            + "against a source-less synthesized node (" + uri + "); a synthesis "
                            + "pass did not thread a source range from its generating context. "
                            + "Falling back to 1:1 -- please report this as a jverify bug.");
                    return new Position(1, 1);
                }
                throw new RuntimeException("Could not find line map for " + uri);
            }
            long line = lineMap.getLineNumber(offset);
            long lineStart = lineMap.getStartPosition(line);
            long column = offset - lineStart;
            return new Position((int)line, (int)column + 1);
        };

        return new AnalysisResult(result, filesMap);
    }

    SourceRange toSourceRange(JCTree node) {
        int startPos = TreeInfo.getStartPos(node);
        int endPos = TreeInfo.getEndPos(node, currentCompilationUnit.endPositions);
        if (endPos == -1) {
            endPos = startPos;
        }
        return new SourceRange(startPos, endPos);
    }

    private List<Command> getPredefinedTypes() {
        return List.of(
            makeConstrainedType("int8", -128L, 127L),
            makeConstrainedType("int16", -32768L, 32767L),
            makeConstrainedType("int32", -2147483648L, 2147483647L),
            makeConstrainedType("int64", -9223372036854775808L, 9223372036854775807L),
            makeConstrainedType("char", 0L, 65535L),
            // @Nat counterparts: the value must be a natural number (>= 0). The upper
            // bound is the same as the corresponding signed type, so e.g. a @Nat int
            // ranges over 0..2^31-1.
            makeConstrainedType("nat7", 0L, 127L),
            makeConstrainedType("nat15", 0L, 32767L),
            makeConstrainedType("nat31", 0L, 2147483647L),
            makeConstrainedType("nat63", 0L, 9223372036854775807L),
            // @Unbounded @Nat: a natural number without an upper bound.
            makeConstrainedType("nat", 0L, null)
        );
    }

    /**
     * Create a constrained integer type {@code name} bounded by {@code x >= min} and
     * {@code x <= max}. Either bound may be null to leave that side unconstrained, e.g.
     * {@code min = 0, max = null} yields the unbounded {@code nat}.
     */
    private Command makeConstrainedType(String name, Long min, Long max) {
        var sr = toSourceRange(currentCompilationUnit);
        var x = identifier(sr, "x");
        var bounds = new ArrayList<StmtExpr>();
        if (min != null) {
            bounds.add(ge(sr, x, longLiteral(sr, min)));
        }
        if (max != null) {
            bounds.add(le(sr, x, longLiteral(sr, max)));
        }
        var constraint = bounds.stream().reduce((a, b) -> and(sr, a, b)).orElse(literalBool(sr, true));
        var witness = int_(sr, 0);
        return constrainedTypeCommand(sr, constrainedType(sr, name, "x", intType(sr), constraint, witness));
    }

    /** Create a Laurel integer literal for any long value, wrapping negatives in Neg. */
    private static StmtExpr longLiteral(SourceRange sr, long val) {
        if (val >= 0) return int_(sr, val);
        return neg(sr, new StmtExpr.Int(sr, java.math.BigInteger.valueOf(val).negate()));
    }

    private LaurelType translateType(com.sun.tools.javac.code.Type type) {
        return translateType(type, null);
    }

    /**
     * Translate a Java type to a Laurel type, honouring {@code @Nat} / {@code @Unbounded}
     * type-use annotations also present on the given modifiers.
     *
     * <p>These annotations can reach us in two ways: attached to the resolved
     * {@link com.sun.tools.javac.code.Type} (e.g. on method parameters and return types),
     * or only on the declaration's modifiers (e.g. on local variable declarations, where
     * javac strips the type-use annotation off the resolved type and the declared type
     * tree). We look in both places via {@code modifiers}, which may be {@code null} when
     * no declaration is available.
     */
    private LaurelType translateType(com.sun.tools.javac.code.Type type, JCTree.JCModifiers modifiers) {
        // Class / interface types (including sealed hierarchies and
        // records): encode as an opaque Laurel CompositeType named
        // after the source-side type. Strata treats unbound composite
        // types as uninterpreted reference sorts, which is enough
        // for parameter-position acceptance (e.g.
        // `static void leftIdentityNone(PathLengthRange r)`).
        // Body-level operations on the value (instanceof, pattern
        // match, record-component reads, constructors) need
        // additional translation that is NOT part of this commit;
        // they will surface as separate convertExpression errors.
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            String name = classType.tsym.getQualifiedName().toString();
            // Use the fully-qualified name (with the `$` nested-class
            // separator normalised to `.`) as the CompositeType sort
            // name. Keeping the package makes the name stable and
            // collision-free across two same-named classes in
            // different packages.
            String sortName = name.replace('$', '.');
            referencedCompositeTypes.add(sortName);
            return compositeType(sortName);
        }
        // @Nat constrains the value to be a natural number (>= 0); @Unbounded drops the
        // fixed-width overflow bound and treats the value as a mathematical integer.
        boolean isNat = JVerifyUtils.isAnnotated(type, org.strata.jverify.Nat.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Nat.class);
        boolean isUnbounded = JVerifyUtils.isAnnotated(type, org.strata.jverify.Unbounded.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Unbounded.class);

        return switch (type.getTag()) {
            case INT -> integerType(isNat, isUnbounded, "int32", "nat31");
            case SHORT -> integerType(isNat, isUnbounded, "int16", "nat15");
            case BYTE -> integerType(isNat, isUnbounded, "int8", "nat7");
            case LONG -> integerType(isNat, isUnbounded, "int64", "nat63");
            case CHAR -> compositeType("char");
            case BOOLEAN -> boolType();
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    /**
     * Pick the Laurel type for an integral Java type given its {@code @Nat}/{@code @Unbounded}
     * annotations. {@code bounded}/{@code boundedNat} are the fixed-width type names for the
     * default and {@code @Nat} cases; {@code @Unbounded} maps to the unbounded {@code int}
     * (or {@code nat} when also {@code @Nat}).
     */
    private LaurelType integerType(boolean isNat, boolean isUnbounded, String bounded, String boundedNat) {
        if (isUnbounded) {
            return isNat ? compositeType("nat") : intType();
        }
        return compositeType(isNat ? boundedNat : bounded);
    }

    private static String qualifiedMethodName(Symbol.MethodSymbol sym) {
        // Package-qualified, immediately-enclosing class name ('$' -> '.') + '_'
        // + method. The immediate (not outermost) class keeps nested-class methods
        // distinct: `Outer.Inner.foo` -> `Outer.Inner_foo`, not the `Outer.foo`-
        // colliding `Outer_foo`. The package keeps it cross-class stable.
        //
        // Some invocations (record accessors of types in an anonymous class, or
        // built-ins on synthetic Symtab entries) have no enclosing ClassSymbol --
        // enclClass() returns null there (where outermostClass() used to throw).
        // Fall back to the immediate owner so such a symbol degrades gracefully
        // instead of aborting the whole source file.
        Symbol.ClassSymbol enclosing;
        try {
            enclosing = sym.enclClass();
        } catch (ClassCastException e) {
            enclosing = null;
        }
        if (enclosing != null) {
            return enclosing.getQualifiedName().toString().replace('$', '.') + "_" + sym.name;
        }
        Symbol owner = sym.owner;
        String prefix = (owner != null && owner.name != null)
                ? owner.name.toString()
                : "$unknown";
        return prefix + "_" + sym.name;
    }

    /**
     * Refuse overloaded methods. The flat {@code Class_method} mangling collapses
     * every overload of a name onto a single Laurel procedure name, so two
     * declarations like {@code void bar(int)} and {@code void bar(String)} would
     * silently collide. Detect this by counting the source-declared (non-synthetic)
     * methods of that name in the enclosing class; refuse with a clear diagnostic
     * when there is more than one. Synthetic members (e.g. record accessors and the
     * canonical constructor) are excluded so records aren't mis-counted.
     */
    private static void refuseIfOverloaded(Symbol.MethodSymbol sym) {
        int sameName = 0;
        for (Symbol member : sym.enclClass().members().getSymbolsByName(sym.name)) {
            if (member instanceof Symbol.MethodSymbol && (member.flags() & Flags.SYNTHETIC) == 0) {
                sameName++;
            }
        }
        if (sameName > 1) {
            throw new JavaViolationException(
                    "overloaded method '" + sym.name + "' (enclosing class declares "
                            + sameName + " methods with this name); overloading is not supported");
        }
    }

    private static boolean isStatic(JCTree.JCMethodDecl method) {
        return (method.mods.flags & Flags.STATIC) != 0;
    }

    /**
     * Whether {@code sym} is a non-static instance field whose value is not a
     * compile-time constant. Reading or writing such a field needs a {@code self#x}
     * field access against the enclosing composite, which carries no fields yet.
     * Static fields and compile-time constants are handled elsewhere.
     */
    private static boolean isNonConstantInstanceField(Symbol sym) {
        return sym instanceof Symbol.VarSymbol varSym
                && varSym.getKind() == javax.lang.model.element.ElementKind.FIELD
                && (varSym.flags() & Flags.STATIC) == 0
                && varSym.getConstValue() == null;
    }

    /**
     * Refuse an instance-field access. Field reads/writes require the composite
     * to carry fields and {@code self#x} access syntax, which lands with the
     * constructor/field work (deferred). Until then, refuse so a getter or
     * mutator surfaces as a graceful skip rather than an unresolved Laurel name
     * or an unsound empty-modifies frame. Declared as returning {@link StmtExpr}
     * so it can stand in expression position; it always throws.
     */
    private static StmtExpr refuseFieldAccess() {
        throw new JavaViolationException(
                "instance field access is not yet supported");
    }

    /**
     * Whether the method returns a primitive integral/char type, which lowers to
     * a Laurel constrained type (int8/int16/int32/int64/char). Strata cannot yet
     * carry a constrained return on a transparent {@code function}, so such a
     * {@code @Pure} method must be emitted as an opaque {@code procedure}.
     */
    private static boolean hasConstrainedReturn(JCTree.JCMethodDecl method) {
        if (method.restype == null || method.restype.type == null) {
            return false;
        }
        return switch (method.restype.type.getTag()) {
            case INT, SHORT, BYTE, LONG, CHAR -> true;
            default -> false;
        };
    }

    /**
     * A translated procedure together with the bookkeeping needed for the
     * transitive-emittability fixpoint: the source method (its symbol is the
     * stable identity used for dropping; the decl is needed to demote it to
     * Skipped), its emitted Laurel name, and the symbols of the user methods
     * its body calls. If any referenced callee is not ultimately emitted, this
     * procedure must be dropped too (else Strata reports an unresolved name),
     * which may cascade to its own callers. The callee set is keyed on method
     * symbols, not mangled names, so two distinct methods that happen to mangle
     * to the same Laurel name don't alias each other in the fixpoint.
     */
    record EmittedProcedure(Procedure procedure, JCTree.JCMethodDecl methodDecl,
                            JCTree.JCCompilationUnit compilationUnit,
                            String mangledName, Set<Symbol.MethodSymbol> referencedCallees) {}

    private class StaticMethodCollector extends TreeScanner {
        final List<EmittedProcedure> procedures = new ArrayList<>();
        /** Callee mangled names referenced by the method currently being translated. */
        private Set<Symbol.MethodSymbol> currentReferencedCallees = null;
        /** True while converting a requires/ensures expression (contract context). */
        private boolean inContractContext = false;
        private int labelCounter = 0;

        /** Label stack entry for break/continue resolution. */
        private record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {}
        private final Deque<LabelEntry> labelStack = new ArrayDeque<>();
        private String pendingLabel = null;

        /** Check if a statement tree contains break or continue (at the current loop level). */
        private boolean containsBreakOrContinue(JCTree.JCStatement stmt) {
            boolean[] found = {false};
            stmt.accept(new TreeScanner() {
                @Override public void visitBreak(JCTree.JCBreak tree) { found[0] = true; }
                @Override public void visitContinue(JCTree.JCContinue tree) { found[0] = true; }
                // Don't descend into nested loops — their break/continue don't target us
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) {}
                @Override public void visitForLoop(JCTree.JCForLoop tree) {}
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) {}
            });
            return found[0];
        }

        /** Check if a method body contains any loop. A @Pure function with
         *  a loop cannot be a transparent (pure-expression) function body --
         *  Strata function bodies allow only let-bindings and a final
         *  if-then-else -- so we emit such a function uninterpreted. */
        private boolean bodyContainsLoop(JCTree.JCBlock body) {
            boolean[] found = {false};
            body.accept(new TreeScanner() {
                @Override public void visitForLoop(JCTree.JCForLoop tree) { found[0] = true; }
                // Foreach is future-proofing: it is not yet reachable here
                // (there is no supported iterable type and no enhanced-for
                // translation case), so it cannot be exercised by a test yet.
                @Override public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) { found[0] = true; }
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) { found[0] = true; }
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) { found[0] = true; }
            });
            return found[0];
        }

        private String freshLabel(String prefix) {
            return prefix + "_" + (labelCounter++);
        }

        /** Set up loop labels, push to stack, execute body, pop stack. */
        private StmtExpr withLoopLabels(JCTree.JCStatement loopBody, java.util.function.BiFunction<String, String, StmtExpr> body) {
            boolean needsLabels = pendingLabel != null || containsBreakOrContinue(loopBody);
            String breakLbl = needsLabels ? freshLabel("loop_break") : null;
            String continueLbl = needsLabels ? freshLabel("loop_continue") : null;
            String javaLabel = pendingLabel;
            pendingLabel = null;
            labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
            try {
                return body.apply(breakLbl, continueLbl);
            } finally {
                labelStack.pop();
            }
        }

        private String resolveBreakLabel(JCTree.JCBreak brk) {
            if (brk.label != null) {
                String target = brk.label.toString();
                for (var entry : labelStack) {
                    if (target.equals(entry.javaLabel)) return entry.breakLabel;
                }
            } else if (!labelStack.isEmpty()) {
                return labelStack.peek().breakLabel;
            }
            throw new JavaViolationException("break target not found");
        }

        private String resolveContinueLabel(JCTree.JCContinue cont) {
            if (cont.label != null) {
                String target = cont.label.toString();
                for (var entry : labelStack) {
                    if (target.equals(entry.javaLabel) && entry.continueLabel != null) return entry.continueLabel;
                }
            } else {
                for (var entry : labelStack) {
                    if (entry.continueLabel != null) return entry.continueLabel;
                }
            }
            throw new JavaViolationException("continue target not found");
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            // Skip, without translating or reporting, members with no
            // user-authored contract to verify. Silent skipping matches the
            // prior behaviour (the old STATIC gate skipped these), so no
            // previously-green class regresses to a spurious diagnostic.
            boolean skip = (method.mods.flags & Flags.SYNTHETIC) != 0
                    // Constructors (deferred): emitting Class_<init> would embed
                    // '<init>' in the name and expose the synthesized super() chain.
                    || JVerifyUtils.isConstructor(method.sym)
                    // Anonymous/local classes have no qualified name, so their
                    // methods would all mangle to `_method` and collide.
                    || method.sym.enclClass().getQualifiedName().isEmpty()
                    // @Verify(false): opted out; body already stripped, so a
                    // procedure shell would be empty (and already counted Skipped).
                    || annotationCompiler.isSkipped(currentCompilationUnit, method);
            if (!skip) {
                boolean methodIsStatic = isStatic(method);
                try {
                    translateMethod(method);
                } catch (JavaViolationException e) {
                    // Demote to Skipped so an un-emitted method isn't counted
                    // Verified. Report a diagnostic only for static methods:
                    // instance-method support is partial, so their bodies often
                    // hit not-yet-supported constructs, and reporting each would
                    // be noise. Static methods keep the #398 skip-with-diagnostic.
                    annotationCompiler.markSkipped(currentCompilationUnit, method);
                    if (methodIsStatic) {
                        reporter.reportError(method, "translatorError", e.getMessage());
                    }
                }
            }
            super.visitMethodDef(method);
        }

        private void translateMethod(JCTree.JCMethodDecl method) {
            boolean methodIsStatic = isStatic(method);

            // Collect the user-method callees referenced while translating this
            // body, for the transitive-emittability fixpoint (see analyzeJavaCode).
            currentReferencedCallees = new LinkedHashSet<>();

            // TODO: when overloaded methods are supported, disambiguate names
            //  (e.g. by appending parameter type suffixes) to avoid duplicate procedure names in Laurel.
            refuseIfOverloaded(method.sym);
            String methodName = qualifiedMethodName(method.sym);

            List<Parameter> params = new ArrayList<>();
            if (!methodIsStatic) {
                // Instance methods take the receiver as an explicit first
                // `self` parameter, so calls lower to Laurel .StaticCall rather
                // than .InstanceCall/.This (which Strata does not yet support).
                // TODO(strata-gap-1): when strata-org/Strata#1172 lands, emit
                // native instance calls (obj#method) and drop the `self` param.
                referencedCompositeTypes.add(
                        method.sym.enclClass().getQualifiedName().toString().replace('$', '.'));
                params.add(parameter("self", translateType(method.sym.enclClass().type)));
            }
            for (var param : method.params) {
                params.add(parameter(toSourceRange(param), param.name.toString(), translateType(param.type, param.mods)));
            }

            Optional<ReturnType> retType = Optional.empty();
            if (method.restype != null && method.restype.type != null
                    && method.restype.type.getTag() != TypeTag.VOID) {
                retType = Optional.of(returnType(toSourceRange(method.restype), translateType(method.restype.type)));
            }

            List<RequiresClause> requires = new ArrayList<>();
            List<EnsuresClause> ensures = new ArrayList<>();
            StmtExpr methodBody = null;

            if (method.body != null) {
                MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                // Refuse object allocation inside a contract expression (see the
                // JCNewClass arm) by marking the context for the duration of
                // pre/post conversion.
                inContractContext = true;
                try {
                    for (var pre : contract.preconditions()) {
                        var preExpr = pre.get();
                        StmtExpr converted = (preExpr instanceof JCTree.JCLambda lambda)
                                ? convertLambdaBody(lambda, Map.of())
                                : convertExpression(preExpr);
                        // Thread the originating clause's source range so a
                        // Strata diagnostic on this (possibly renamed/synthesized)
                        // clause points at the user's precondition rather than the
                        // synthetic "<unknown>" path.
                        requires.add(requiresClause(toSourceRange(preExpr), converted, Optional.empty()));
                    }
                    for (var post : contract.postconditions()) {
                        var postExpr = post.get();
                        if (postExpr instanceof JCTree.JCLambda lambda) {
                            // A 1-param postcondition lambda binds the return
                            // value (renamed to Laurel's canonical
                            // LAUREL_RESULT_BINDING); a 0-param lambda
                            // (postcondition(BooleanSupplier), e.g. on a void
                            // method) captures the enclosing scope directly and
                            // needs no rename.
                            Map<String, String> renames = lambda.params.size() == 1
                                    ? Map.of(lambda.params.getFirst().name.toString(), LAUREL_RESULT_BINDING)
                                    : Map.of();
                            // Thread the postcondition lambda's source range: when
                            // the renamed `result` binding collides with a user
                            // parameter, Strata's duplicate-definition diagnostic
                            // then points at this `ensures` clause instead of the
                            // synthetic "<unknown>" path.
                            ensures.add(ensuresClause(toSourceRange(postExpr), convertLambdaBody(lambda, renames), Optional.empty()));
                        } else {
                            ensures.add(ensuresClause(toSourceRange(postExpr), convertExpression(postExpr), Optional.empty()));
                        }
                    }
                } finally {
                    inContractContext = false;
                }

                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(method.body);
                if (!implStatements.isEmpty()) {
                    List<StmtExpr> stmts = new ArrayList<>();
                    for (var statement : implStatements) {
                        StmtExpr converted = convertStatement(statement);
                        if (converted != null) {
                            stmts.add(converted);
                        }
                    }
                    methodBody = block(toSourceRange(method.body), stmts);
                }
            }

            boolean isPure = jverifyUtils.isPure(method.sym);
            // A @Pure function whose body contains a loop cannot be a
            // transparent (pure-expression) function body, so drop the
            // body and emit it uninterpreted (a sound over-approximation)
            // rather than producing an untranslatable block expression.
            if (isPure && method.body != null && methodBody != null
                    && bodyContainsLoop(method.body)) {
                if (!ensures.isEmpty()) {
                    // Dropping the body would silently discard the
                    // postcondition (it cannot be checked against an
                    // uninterpreted function), so refuse instead.
                    throw new JavaViolationException(
                        "@Pure function with a loop and a postcondition is not yet supported");
                }
                methodBody = null;
            }

            Optional<Body> optBody = methodBody != null
                    ? Optional.of(body(methodBody))
                    : Optional.empty();

            // A pure method stays a transparent `function` only with no
            // postconditions and a non-constrained return; Strata rejects a
            // `function` carrying either (LaurelToCoreTranslator.lean,
            // ConstrainedTypeElim.lean), so otherwise emit an opaque procedure.
            // modifies is always empty: jverify doesn't yet emit modifies clauses.
            // TODO(strata-gap-2): drop these carve-outs when Strata#1173 lands
            // (or when functions are removed, Strata#1352).
            boolean canStayTransparent = isPure && ensures.isEmpty()
                    && !hasConstrainedReturn(method);
            Optional<OpaqueSpec> optSpec = canStayTransparent
                    ? Optional.empty()
                    : Optional.of(opaqueSpec(ensures, List.of()));

            Procedure proc = canStayTransparent
                    ? function(toSourceRange(method), methodName, params,
                            retType, Optional.empty(), requires, Optional.empty(), optSpec, optBody)
                    : procedure(toSourceRange(method), methodName, params,
                            retType, Optional.empty(), requires, Optional.empty(), optSpec, optBody);
            procedures.add(new EmittedProcedure(proc, method, currentCompilationUnit,
                    methodName, currentReferencedCallees));
            currentReferencedCallees = null;
        }

        private StmtExpr convertBlock(JCTree.JCBlock blk, Map<String, String> renames) {
            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement, renames);
                if (converted != null) {
                    statements.add(converted);
                }
            }
            return block(toSourceRange(blk), statements);
        }

        /**
         * Emit a while loop with optional break/continue label wrapping.
         *
         * When labels are present, produces (for a for-loop with break/continue):
         * <pre>
         * labelledBlock(breakLbl, {
         *   preamble...        // e.g. init for for-loops, sentinel decl for do-while
         *   while (cond)
         *     invariants: [...]
         *   {
         *     labelledBlock(continueLbl, { body });
         *     step;            // null for while/do-while
         *   }
         * })
         * </pre>
         * break emits as exit(breakLbl), continue as exit(continueLbl).
         * The step is outside the continue label so continue skips the body but still runs the step.
         * @param sr source range
         * @param cond loop condition
         * @param invariants loop invariants
         * @param loopBody the translated loop body
         * @param step optional step expression (for-loops); null for while/do-while
         * @param preamble statements to emit before the while (e.g. init, sentinel decl)
         * @param breakLbl break label (null if no labels needed)
         * @param continueLbl continue label (null if no labels needed)
         */
        private StmtExpr emitLoop(SourceRange sr, StmtExpr cond, List<InvariantClause> invariants,
                                  StmtExpr loopBody, StmtExpr step, List<StmtExpr> preamble,
                                  String breakLbl, String continueLbl) {
            if (breakLbl != null) {
                StmtExpr wrappedBody = labelledBlock(sr, List.of(loopBody), continueLbl);
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = block(sr, List.of(wrappedBody, step));
                } else {
                    whileBody = wrappedBody;
                }
                StmtExpr whileNode = while_(sr, cond, invariants, whileBody);
                List<StmtExpr> outerStmts = new ArrayList<>(preamble);
                outerStmts.add(whileNode);
                return labelledBlock(sr, outerStmts, breakLbl);
            } else {
                if (step != null) {
                    // Use forLoop IR node for simple for-loops without break/continue
                    StmtExpr init = preamble.isEmpty() ? block(sr, List.of()) : preamble.getFirst();
                    return forLoop(sr, init, cond, step, invariants, loopBody);
                }
                StmtExpr whileNode = while_(sr, cond, invariants, loopBody);
                if (preamble.isEmpty()) {
                    return whileNode;
                }
                List<StmtExpr> stmts = new ArrayList<>(preamble);
                stmts.add(whileNode);
                return block(sr, stmts);
            }
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return convertStatement(statement, Map.of());
        }

        /**
         * Convert a statement that appears in a position requiring a non-null
         * {@link StmtExpr} (an if/else branch, a labeled-statement body, or a
         * non-block loop body). An empty statement ({@code ;}) converts to
         * {@code null}, which cannot be serialized as a Laurel node; substitute
         * an empty block so e.g. {@code if (c) ;} and {@code for (..) ;} are
         * valid no-ops rather than crashing the Ion serializer with an NPE.
         */
        private StmtExpr convertStatementOrEmpty(JCTree.JCStatement statement, Map<String, String> renames) {
            StmtExpr converted = convertStatement(statement, renames);
            return converted != null ? converted : block(toSourceRange(statement), List.of());
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement, Map<String, String> renames) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        assert_(toSourceRange(assertStmt), convertExpression(assertStmt.cond, renames), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr, renames);
                case JCTree.JCBlock blk -> convertBlock(blk, renames);
                case JCTree.JCVariableDecl varDecl -> {
                    LaurelType type = translateType(varDecl.type, varDecl.mods);
                    Optional<Initializer> optAssign = varDecl.init != null
                            ? Optional.of(initializer(convertExpression(varDecl.init, renames)))
                            : Optional.empty();
                    yield varDecl(toSourceRange(varDecl), varDecl.name.toString(),
                            Optional.of(typeAnnotation(type)), optAssign);
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond, renames);
                    StmtExpr thenBranch = convertStatementOrEmpty(ifStmt.thenpart, renames);
                    Optional<ElseBranch> elseB = Optional.empty();
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart, renames);
                        if (elseStmt != null) {
                            elseB = Optional.of(elseBranch(elseStmt));
                        }
                    }
                    yield ifThenElse(toSourceRange(ifStmt), cond, thenBranch, elseB);
                }
                case JCTree.JCReturn retStmt -> {
                    Optional<StmtExpr> value = retStmt.expr != null
                            ? Optional.of(convertExpression(retStmt.expr, renames))
                            : Optional.empty();
                    yield return_(toSourceRange(retStmt), value);
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    var sr = toSourceRange(whileStmt);
                    yield withLoopLabels(whileStmt.body, (breakLbl, continueLbl) -> {
                        StmtExpr cond = convertExpression(whileStmt.cond, renames);
                        var parts = extractLoopParts(whileStmt.body, renames);
                        return emitLoop(sr, cond, parts.invariants, parts.body, null, List.of(),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCForLoop forLoop -> {
                    if (forLoop.init.size() > 1 || forLoop.step.size() > 1)
                        throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                    var sr = toSourceRange(forLoop);
                    yield withLoopLabels(forLoop.body, (breakLbl, continueLbl) -> {
                        StmtExpr init = forLoop.init.isEmpty() ? block(sr, List.of())
                                : convertStatement(forLoop.init.getFirst(), renames);
                        StmtExpr cond = forLoop.cond != null
                                ? convertExpression(forLoop.cond, renames)
                                : literalBool(sr, true);
                        StmtExpr step = forLoop.step.isEmpty() ? block(sr, List.of())
                                : convertStatement(forLoop.step.getFirst(), renames);
                        var parts = extractLoopParts(forLoop.body, renames);
                        return emitLoop(sr, cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhile -> {
                    // Desugar do-while using while(true) + exit(!cond):
                    //   labelledBlock(breakLbl, [
                    //     while (true) invariants:[I] {
                    //       labelledBlock(continueLbl, [ body ]);
                    //       if (!cond) exit(breakLbl);
                    //     }
                    //   ])
                    // The exit(!cond) is OUTSIDE the continueLbl block so that
                    // continue re-evaluates the condition (doesn't skip it).
                    // Invariant is checked at while-entry before every iteration
                    // including the first — no soundness gap as discussed in #418.
                    var sr = toSourceRange(doWhile);
                    // Do-while always needs labels: the desugaring itself uses exit(breakLbl)
                    var breakLbl = freshLabel("loop_break");
                    var continueLbl = freshLabel("loop_continue");
                    var javaLabel = pendingLabel;
                    pendingLabel = null;
                    labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
                    try {
                        var cond = convertExpression(doWhile.cond, renames);
                        var parts = extractLoopParts(doWhile.body, renames);
                        var exitIfDone = ifThenElse(sr, not(sr, cond),
                                exit(sr, breakLbl), Optional.empty());
                        var wrappedBody = labelledBlock(sr, List.of(parts.body), continueLbl);
                        var whileBody = block(sr, List.of(wrappedBody, exitIfDone));
                        var whileNode = while_(sr, literalBool(sr, true), parts.invariants, whileBody);
                        yield labelledBlock(sr, List.of(whileNode), breakLbl);
                    } finally {
                        labelStack.pop();
                    }
                }
                case JCTree.JCBreak breakStmt -> {
                    yield exit(toSourceRange(breakStmt), resolveBreakLabel(breakStmt));
                }
                case JCTree.JCContinue contStmt -> {
                    yield exit(toSourceRange(contStmt), resolveContinueLabel(contStmt));
                }
                case JCTree.JCLabeledStatement labeledStmt -> {
                    // Set the pending label so the next loop picks it up
                    pendingLabel = labeledStmt.label.toString();
                    // If the body is a loop, it will consume pendingLabel
                    // If not, wrap in a labelledBlock for break-out-of-block
                    if (labeledStmt.body instanceof JCTree.JCWhileLoop
                            || labeledStmt.body instanceof JCTree.JCForLoop
                            || labeledStmt.body instanceof JCTree.JCDoWhileLoop) {
                        yield convertStatement(labeledStmt.body, renames);
                    } else {
                        // Non-loop labeled statement: only break is valid (no continue)
                        String breakLbl = freshLabel("label_break");
                        labelStack.push(new LabelEntry(pendingLabel, breakLbl, null));
                        pendingLabel = null;
                        try {
                            StmtExpr body = convertStatementOrEmpty(labeledStmt.body, renames);
                            yield labelledBlock(toSourceRange(labeledStmt), List.of(body), breakLbl);
                        } finally {
                            labelStack.pop();
                        }
                    }
                }
                case JCTree.JCSkip ignored -> null;
                default -> throw new JavaViolationException("Unsupported statement: " + statement.getClass().getSimpleName());
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr) {
            return convertExpression(expr, Map.of());
        }

        private StmtExpr convertLambdaBody(JCTree.JCLambda lambda, Map<String, String> renames) {
            if (lambda.body instanceof JCTree.JCExpression exprBody) {
                return convertExpression(exprBody, renames);
            } else if (lambda.body instanceof JCTree.JCReturn ret && ret.expr != null) {
                return convertExpression(ret.expr, renames);
            } else if (lambda.body instanceof JCTree.JCBlock blk && blk.stats.size() == 1
                    && blk.stats.getFirst() instanceof JCTree.JCReturn ret && ret.expr != null) {
                return convertExpression(ret.expr, renames);
            }
            throw new JavaViolationException("Unsupported lambda body");
        }

        private record LoopParts(List<InvariantClause> invariants, StmtExpr body) {}

        private LoopParts extractLoopParts(JCTree.JCStatement body, Map<String, String> renames) {
            if (body instanceof JCTree.JCBlock loopBlock) {
                // Only loops authored with an explicit invariant
                // block (the contract-bearing shape produced by
                // JVerify's contract authoring convention) have the
                // structure getContract expects. Loop bodies
                // without the wrapping contract block — including
                // javac-synthesized while loops from enhanced-for
                // desugaring — are processed as pure implementation
                // statements with no invariants.
                if (!MethodOrLoopContractCompiler.hasContractStructure(loopBlock)) {
                    List<StmtExpr> stmts = new ArrayList<>();
                    for (var s : loopBlock.getStatements()) {
                        StmtExpr converted = convertStatement(s, renames);
                        if (converted != null) stmts.add(converted);
                    }
                    return new LoopParts(List.of(), block(toSourceRange(loopBlock), stmts));
                }
                MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
                List<InvariantClause> invariants = new ArrayList<>();
                // A loop invariant is a contract expression, like a pre/postcondition:
                // an object allocation (or lambda) in it would leak an un-lowered
                // `new_` block that Strata can't lift out of contract position. Mark
                // the context so such allocations are refused gracefully here too.
                boolean savedContractContext = inContractContext;
                inContractContext = true;
                try {
                    for (var inv : loopContract.loopInvariants()) {
                        // Thread the invariant's source range (see #439) so a
                        // Strata diagnostic points at the user's invariant() call.
                        invariants.add(invariantClause(toSourceRange(inv.get()), convertExpression(inv.get(), renames)));
                    }
                } finally {
                    inContractContext = savedContractContext;
                }
                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
                List<StmtExpr> stmts = new ArrayList<>();
                for (var s : implStatements) {
                    StmtExpr converted = convertStatement(s, renames);
                    if (converted != null) stmts.add(converted);
                }
                return new LoopParts(invariants, block(toSourceRange(loopBlock), stmts));
            } else {
                return new LoopParts(List.of(), convertStatementOrEmpty(body, renames));
            }
        }

        /**
         * The {@code self} argument for an instance call: the converted receiver
         * for an explicit {@code obj.m(...)}, or {@code self} for an implicit-this
         * {@code m(...)}. {@code super.m(...)} is refused — static mangling can't
         * express the super-dispatch target soundly.
         */
        private StmtExpr receiverSelf(JCTree.JCExpression methodSelect, Map<String, String> renames) {
            if (methodSelect instanceof JCTree.JCFieldAccess fieldAccess) {
                var selected = fieldAccess.selected;
                if (selected instanceof JCTree.JCIdent ident
                        && ident.name == ident.name.table.names._super) {
                    throw new JavaViolationException("super call — not yet supported");
                }
                // A `new T(...)` receiver (`new T().m()`) converts to an opaque
                // `new_(T)` value, which has no procedure-typed shape to pass as
                // `self`; emitting the call anyway makes Strata fail to unify the
                // argument. Refuse for a graceful skip until constructor-allocated
                // values can be captured into a temporary (deferred).
                var unwrapped = selected;
                while (unwrapped instanceof JCTree.JCParens parens) {
                    unwrapped = parens.expr;
                }
                if (unwrapped instanceof JCTree.JCNewClass) {
                    throw new JavaViolationException(
                            "method call on a freshly-allocated receiver is not yet supported");
                }
                return convertExpression(selected, renames);
            }
            // Implicit-this call `m(...)` (a bare JCIdent method select): the
            // receiver is the enclosing instance, i.e. the `self` parameter.
            return identifier(toSourceRange(methodSelect), "self");
        }

        /**
         * Refuse a call unless it is provably monomorphic. Static
         * {@code Class_method} mangling resolves a call against the receiver's
         * STATIC type, so any call that could dispatch to an override at runtime
         * (the receiver holds a subtype) would verify against the wrong contract —
         * JVerify enforces no behavioural subtyping. Refuse such calls until
         * runtime dispatch lands (Strata#1174).
         *
         * A call cannot dispatch polymorphically — so is safe — only when the
         * callee is {@code final}/{@code private} or its enclosing class is
         * {@code final} (e.g. {@code String.length()}): then no override can
         * exist. Note this must NOT be keyed on whether the callee itself
         * overrides a supertype — the unsound case is a non-overriding method
         * that IS overridden by a subtype, reached through a supertype-typed
         * reference.
         */
        private void refuseIfPolymorphicDispatch(Symbol.MethodSymbol methodSym) {
            long flags = methodSym.flags();
            boolean cannotDispatch = (flags & (Flags.FINAL | Flags.PRIVATE)) != 0
                    || (methodSym.enclClass().flags() & Flags.FINAL) != 0;
            if (!cannotDispatch) {
                throw new JavaViolationException(
                        "polymorphic dispatch through interface/superclass is not yet supported");
            }
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident when ident.name == ident.name.table.names._this ->
                    // `this` in an instance method body maps to the explicit
                    // `self` receiver parameter.
                    identifier(toSourceRange(ident), "self");
                case JCTree.JCIdent ident when isNonConstantInstanceField(ident.sym) ->
                    // A bare reference to an instance field (implicit `this.x`)
                    // would need a `self#x` field read against the composite,
                    // which carries no fields yet (deferred). Refuse so it
                    // surfaces as a graceful skip rather than an unresolved name.
                    refuseFieldAccess();
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield identifier(toSourceRange(ident), renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        assign(toSourceRange(asgn), convertExpression(asgn.lhs, renames), convertExpression(asgn.rhs, renames));
                case JCTree.JCConditional cond ->
                        ifThenElse(toSourceRange(cond), convertExpression(cond.cond, renames),
                                convertExpression(cond.truepart, renames),
                                Optional.of(elseBranch(toSourceRange(cond.falsepart), convertExpression(cond.falsepart, renames))));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    refuseIfOverloaded(methodSym);
                    boolean calleeStatic = (methodSym.flags() & Flags.STATIC) != 0;
                    List<StmtExpr> args = new ArrayList<>();
                    if (!calleeStatic) {
                        // Instance call: prepend the receiver as the `self`
                        // argument. Compute the receiver first so its own
                        // refusals (super-call, freshly-allocated receiver) win
                        // when they apply — those name the precise unsupported
                        // shape, whereas the polymorphic-dispatch refusal is the
                        // general fallback for any non-monomorphic call. Then
                        // refuse polymorphic dispatch, since static mangling
                        // would route a supertype-typed call to the wrong
                        // override.
                        // TODO(strata-gap-1): emit obj#method when Strata#1172 lands.
                        // TODO(strata-gap-3): drop this refusal when Strata#1174
                        // adds runtime dispatch.
                        var selfArg = receiverSelf(invocation.getMethodSelect(), renames);
                        refuseIfPolymorphicDispatch(methodSym);
                        args.add(selfArg);
                    }
                    String calleeName = qualifiedMethodName(methodSym);
                    // Record calls to user methods we emit (have a source tree,
                    // not anon/local), so the fixpoint drops this caller if the
                    // callee isn't emitted. Library/contract methods (no source
                    // tree) resolve elsewhere and aren't tracked.
                    if (currentReferencedCallees != null
                            && index.getTree(methodSym) != null
                            && !methodSym.enclClass().getQualifiedName().isEmpty()) {
                        currentReferencedCallees.add(methodSym);
                    }
                    for (var arg : invocation.args) {
                        args.add(convertExpression(arg, renames));
                    }
                    yield call(toSourceRange(invocation),
                            identifier(toSourceRange(invocation), calleeName), args);
                }
                // Synthesized by SwitchDesugarer to hoist a switch-expression's
                // selector into a fresh local; lowers to a Laurel block (StmtExpr,
                // usable in expression position). javac's Lower emits LetExpr for
                // boxed compound-assign, postops, and pattern switches — all
                // currently unsupported.
                case JCTree.LetExpr letExpr -> {
                    List<StmtExpr> stmts = new ArrayList<>();
                    for (var def : letExpr.defs) {
                        StmtExpr converted = convertStatement(def, renames);
                        if (converted != null) stmts.add(converted);
                    }
                    stmts.add(convertExpression(letExpr.expr, renames));
                    yield block(toSourceRange(letExpr), stmts);
                }
                // Compile-time constant field access (e.g. `Foo.CONST` where
                // CONST is `static final int CONST = 42`). javac keeps these as
                // JCFieldAccess with the constant value attached on the
                // expression's type.
                case JCTree.JCFieldAccess fa when fa.type.constValue() != null ->
                    convertConstantValue(toSourceRange(fa), fa.type.getTag(), fa.type.constValue());
                case JCTree.JCFieldAccess fa when isNonConstantInstanceField(fa.sym) ->
                    // `this.x` / `obj.x` instance-field read: needs `self#x` on a
                    // composite that carries fields (deferred). Refuse for now.
                    refuseFieldAccess();
                case JCTree.JCNewClass newClass -> {
                    // `new T(...)` for class / record types: produce
                    // a Laurel `new_(T)` value of the matching
                    // CompositeType. Constructor arguments are NOT
                    // captured into the resulting value yet — that
                    // would need a Laurel datatype declaration with
                    // constructor args matching the source. For
                    // verification of identity-style properties
                    // that compare references (e.g. `cover(None, r)
                    // == r`) the opaque value is sufficient. Body-
                    // level inspection of record components will
                    // still error until the datatype encoding
                    // lands.
                    if (inContractContext) {
                        // `new` (and lambdas, which lower to `new <anon>()`)
                        // produces a heap-allocating block. Strata lifts such
                        // blocks out of bodies but not out of contract expressions,
                        // where they reach Core unlowered ("block expression should
                        // have been lowered"). Refuse until Strata lifts in contracts.
                        throw new JavaViolationException(
                                "object allocation (including lambdas) inside a contract is not yet supported");
                    }
                    SourceRange sr = toSourceRange(newClass);
                    String name = newClass.type.tsym
                            .getQualifiedName().toString()
                            .replace('$', '.');
                    // Declare the opaque composite sort even when the type
                    // appears only here (in `new T(...)`) and never in a
                    // type position, so the new_(T) value resolves.
                    referencedCompositeTypes.add(name);
                    // Translate each argument so unsupported argument
                    // expressions still surface as errors, but DISCARD
                    // the result: the opaque new_(T) value models only
                    // the reference identity, not the constructor's
                    // arguments or their side effects. Capturing those
                    // needs a Laurel datatype encoding and expression
                    // sequencing (let/temporaries), which is future
                    // work.
                    for (var arg : newClass.args) {
                        convertExpression(arg, renames);
                    }
                    yield new_(sr, name);
                }
                case JCTree.JCInstanceOf instanceOf -> {
                    // `r instanceof X`: the opaque-CompositeType
                    // encoding carries no runtime tag, so the test
                    // cannot be modelled precisely yet. Fail with a
                    // clear, attributable error rather than emitting an
                    // undeclared `instanceOf_<X>` predicate symbol
                    // (which would surface only as a confusing
                    // downstream "Resolution failed" message, and whose
                    // simple-name form could even collide across
                    // packages). A precise encoding needs a tagged
                    // datatype representation; future work.
                    throw new JavaViolationException(
                        "instanceof on opaque reference types is not yet supported");
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return convertConstantValue(toSourceRange(literal), literal.typetag, literal.value);
        }

        /// Lower a primitive compile-time constant value to a Laurel literal.
        /// Used by JCLiteral and JCFieldAccess (where the value is attached
        /// via Type.constValue()). Boolean values arrive as Integer 0/1 from
        /// Type.constValue() — Java represents booleans as int internally.
        private StmtExpr convertConstantValue(SourceRange sr, TypeTag tag, Object v) {
            return switch (tag) {
                case BOOLEAN -> literalBool(sr, ((Number) v).intValue() != 0);
                case CHAR, INT, SHORT, BYTE, LONG -> longLiteral(sr, ((Number) v).longValue());
                default -> throw new JavaViolationException("Unsupported constant type tag: " + tag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            StmtExpr lhs = convertExpression(binary.lhs, renames);
            StmtExpr rhs = convertExpression(binary.rhs, renames);
            SourceRange sr = toSourceRange(binary);
            return switch (binary.getTag()) {
                case PLUS -> add(sr, lhs, rhs);
                case MINUS -> sub(sr, lhs, rhs);
                case MUL -> mul(sr, lhs, rhs);
                case DIV -> divT(sr, lhs, rhs);
                case MOD -> modT(sr, lhs, rhs);
                case EQ -> eq(sr, lhs, rhs);
                case NE -> neq(sr, lhs, rhs);
                case GT -> gt(sr, lhs, rhs);
                case LT -> lt(sr, lhs, rhs);
                case GE -> ge(sr, lhs, rhs);
                case LE -> le(sr, lhs, rhs);
                case AND -> andThen(sr, lhs, rhs);
                case OR -> orElse(sr, lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            StmtExpr inner = convertExpression(unary.arg, renames);
            SourceRange sr = toSourceRange(unary);
            return switch (unary.getTag()) {
                case NOT -> not(sr, inner);
                case NEG -> neg(sr, inner);
                case PREINC -> preIncr(sr, inner);
                case PREDEC -> preDecr(sr, inner);
                case POSTINC -> postIncr(sr, inner);
                case POSTDEC -> postDecr(sr, inner);
                default -> throw new JavaViolationException("Unsupported unary op: " + unary.getTag());
            };
        }

        private StmtExpr convertJVerifyCall(JCTree.JCMethodInvocation invocation,
                                            Symbol.MethodSymbol jverifyMethod,
                                            Map<String, String> renames) {
            var name = jverifyMethod.getQualifiedName().toString();
            SourceRange sr = toSourceRange(invocation);
            return switch (name) {
                case "check" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("check should have a single argument");
                    yield assert_(sr, convertExpression(invocation.args.getFirst(), renames), Optional.empty());
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield assume(sr, convertExpression(invocation.args.getFirst(), renames));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    yield or(sr, not(sr, convertExpression(invocation.args.get(0), renames)),
                            convertExpression(invocation.args.get(1), renames));
                }
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr qBody = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
                        var ty = translateType(p.type, p.mods);
                        qBody = name.equals("forall")
                                ? forallExpr(sr, p.name.toString(), ty, Optional.empty(), qBody)
                                : existsExpr(sr, p.name.toString(), ty, Optional.empty(), qBody);
                    }
                    yield qBody;
                }
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}
