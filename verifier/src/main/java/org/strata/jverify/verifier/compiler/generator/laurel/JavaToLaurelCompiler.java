package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.*;
import org.strata.jverify.verifier.VerifierOptions;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.LoweredResult;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.frontend.JavaLowerer;
import org.strata.jverify.verifier.compiler.simplifications.JVerifyUtils;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import org.strata.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import org.strata.jverify.verifier.laurel.FilesMap;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.*;
import java.util.Optional;

public class JavaToLaurelCompiler {
    /// Since Strata commit 72f3a6e the short `: T` return form auto-generates
    /// an output parameter named `$result` (a reserved name), not `result`.
    /// Name of the synthesized receiver parameter on instance-method procedures.
    /// '?' is outside Java's identifier grammar (JLS 3.8 -- unlike '$', which is
    /// legal) but inside Strata's, so no user parameter or local can collide with
    /// it. {@link #MANGLE_SEPARATOR} relies on the same property.
    private static final String LAUREL_SELF_BINDING = "self?";

    private static final String LAUREL_RESULT_BINDING = "$result";
    /// Synthesized name binding the thrown value in a procedure's `throws (e: T)`
    /// clause. Referenced by `throwsOn` case postconditions once the exceptional
    /// contract DSL lands; until then it is declared and unused.
    private static final String THROWN_BINDING = "java?thrown";

    /**
     * Wrap modifies {@code targets} into the degenerate group form every frontend
     * emits: one unguarded {@link ModifiesGroup} that is always present, even when
     * the target list is empty (an empty group is the opaque default "nothing
     * changes" frame -- dropping it would leave the procedure unframed). Guards
     * are attached later, inside Strata, by {@code EliminateExceptions}.
     */
    private static List<ModifiesGroup> unguardedModifies(List<AstNode<StmtExpr>> targets) {
        return List.of(new ModifiesGroup(targets, Optional.empty(), Optional.empty()));
    }

    /**
     * The same procedure with its body reduced to an opaque DECLARATION: no
     * implementation, no postconditions, empty frame. Preconditions are kept (a
     * caller still has to establish them, which is the stronger obligation), as are
     * the signature and any declared exceptional exit.
     *
     * <p>Every clause this drops weakens what a caller may assume, so dropping is
     * sound. The EMPTY FRAME it installs does not: it is a positive promise that the
     * call changes nothing. So this is only applicable to a procedure whose frame is
     * already empty -- which is what {@link #keepsOpaqueDeclaration} decides, and it
     * is NOT implied by purity: a heap-writing {@code @Pure} method legitimately
     * carries the synthesized wildcard frame (see the {@code bodyPotentiallyWritesHeap}
     * fallback in {@code translateMethod}).
     *
     * <p>{@code throwsOn} is passed through unchanged rather than emptied, which is
     * only honest because {@link #keepsOpaqueDeclaration} also requires it to be
     * empty: those blocks hold the user's exceptional POSTconditions, and passing
     * them through a declaration nothing verified would have callers assume unchecked
     * guarantees.
     */
    private static Procedure declarationOnly(Procedure p) {
        return new Procedure(p.name(), p.typeArgs(), p.inputs(), p.outputs(),
                p.preconditions(), p.contracts(), p.decreases(),
                new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of())),
                p.invokeOn(), p.isInterpretEntry(), p.axioms(),
                p.throwsType(), p.throwsBinding(), p.throwsOn(),
                p.readsGlobals(), p.writesGlobals());
    }

    /**
     * Whether a skipped procedure may be reduced by {@link #declarationOnly} rather
     * than dropped: exactly when the two clauses that method cannot honestly
     * reproduce are already absent.
     *
     * <ul>
     * <li>The FRAME must already be empty. Replacing a real frame with an empty one
     * lets a caller assume the heap is unchanged across a call that writes it.
     * <li>There must be no {@code throwsOn} block, because {@link #declarationOnly}
     * passes those through and they carry exceptional postconditions -- guarantees
     * nothing checked.
     * </ul>
     *
     * <p>A body with no frame FIELD ({@code Transparent}, {@code Abstract},
     * {@code External}) counts as frameless: only {@code Body.Opaque} can carry one.
     */
    private static boolean keepsOpaqueDeclaration(Procedure p) {
        return frameIsEmpty(p.body()) && p.throwsOn().isEmpty();
    }

    /** True when {@code body} frames nothing. See {@link #keepsOpaqueDeclaration}. */
    private static boolean frameIsEmpty(Body body) {
        return !(body instanceof Body.Opaque opaque)
                || opaque.modifies().stream().allMatch(group -> group.targets().isEmpty());
    }
    /// javac's synthetic source path for trees injected by a simplification
    /// pass; Strata reports diagnostics for them against this URI, which has
    /// no entry in our line map.
    private static final String SYNTHETIC_UNKNOWN_PATH = "/<unknown>";

    /**
     * When set, a diagnostic against a source-less synthesized node throws instead
     * of warning. The build turns this on for every test task: such a diagnostic is a
     * jverify bug that is invisible in normal output, so only the suite can catch it.
     * Production runs leave it off and degrade gracefully via the {@code FilesMap}
     * fallback below.
     */
    private static final String STRICT_SOURCE_RANGES_PROPERTY = "jverify.strictSourceRanges";

    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    private final Reporter reporter;
    private final VerifyAnnotationCompiler annotationCompiler;
    JCTree.JCCompilationUnit currentCompilationUnit;

    /// Sort name -> the javac symbol it came from. The symbol lets emission derive
    /// each sort's Java supertype closure for its `extends` edges. First symbol seen
    /// for a name wins (later references name the same type); insertion order is
    /// preserved for deterministic output.
    /// The composite sort at the root of the reference hierarchy. Declared
    /// parentless; `addParent` gives every other sort a path to it.
    private static final String OBJECT_SORT = "java.lang.Object";

    private final com.sun.tools.javac.code.Symtab symtab;

    private final Map<String, Symbol.TypeSymbol> referencedCompositeTypes = new LinkedHashMap<>();
    /**
     * The source files THIS compilation owns. A class declared in one of them is a
     * program type whose composite we emit and whose values the program allocates;
     * anything else is a library type, which may be modelled as a Laurel value
     * (String, the boxed primitives) rather than a composite. Used to decide whether
     * a runtime type test is meaningful -- javac sets {@code sourcefile} on JDK
     * symbols too when platform sources are available, so that flag alone does not
     * distinguish them.
     */
    private final Set<javax.tools.JavaFileObject> ownSourceFiles = new java.util.HashSet<>();
    /**
     * The method being translated, so a type variable can be matched to its
     * DECLARER rather than by name. A type parameter the method itself declares
     * ({@code T} in {@code static <T> void f(T x)}) becomes a Laurel
     * {@code HighType.TVar} and is listed in the procedure's {@code typeArgs},
     * so Strata monomorphizes the procedure per call site and infers the type
     * arguments there.
     *
     * <p>Comparing the owner SYMBOL, not the name, is what makes this safe: a
     * class type parameter is owned by a {@code ClassSymbol} and so never
     * matches, even when it shadows a method parameter of the same name (as
     * {@code GenContainer<T>.add} and {@code <T> uToThree} do in
     * {@code PolymorphicLambdas}), and a value left over from an earlier method
     * cannot make a class type parameter translate by accident.
     */
    private Symbol.MethodSymbol currentMethodSymbol = null;
    /// Reasons already reported per method, so a construct hit repeatedly in one body
    /// warns once rather than per occurrence.
    ///
    /// Keyed on the SYMBOL, not a rendering of it. A string key identifies a method
    /// only as well as its spelling: `MethodSymbol.toString()` is `name(paramTypes)`
    /// with no owner, and `owner.toString()` is the class's name, shared by two local
    /// classes declared in different methods -- so a string key drops the second of
    /// two refusals, the silence this reporting exists to remove. javac gives each
    /// declaration its own Symbol instance, so identity is exact.
    private final Map<Symbol.MethodSymbol, Set<String>> reportedInstanceRefusals = new HashMap<>();

    /// Records `reason` for `sym`, returning whether it is new (i.e. worth reporting).
    /** One demotion round: mark every method that calls an already-refused one, and
        report whether it demoted anything. `markSkipped` stays the single writer of
        refusal state, so progress is read back from the authoritative set rather than
        from a counter -- an anonymous TreeScanner cannot assign to a captured local. */
    private boolean demoteCallersOfRefusedMethods(LoweredResult loweredResult) {
        final var refusedSymbols = annotationCompiler.skippedMethodSymbols();
        final int refusedBefore = refusedSymbols.size();
        for (var compilationUnit : loweredResult.parsed()) {
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            final JCTree.JCCompilationUnit unit = compilationUnit;
            unit.accept(new TreeScanner() {
                private JCTree.JCMethodDecl current = null;

                @Override
                public void visitMethodDef(JCTree.JCMethodDecl method) {
                    var saved = current;
                    current = method;
                    super.visitMethodDef(method);
                    current = saved;
                }

                @Override
                public void visitApply(JCTree.JCMethodInvocation tree) {
                    if (current != null && current.sym != null && current.body != null
                            && !refusedSymbols.contains(current.sym)
                            && TreeInfo.symbol(tree.getMethodSelect())
                                    instanceof Symbol.MethodSymbol callee
                            && refusedSymbols.contains(callee)
                            // A skipped PURE callee still gets an opaque declaration
                            // emitted (see `declarationOnly`), so the call resolves and
                            // the caller stays verifiable -- it just learns nothing
                            // about the result. Only a non-pure callee, which we cannot
                            // give an honest frame, still demotes its callers.
                            && !keepsDeclarationWhenSkipped(callee)) {
                        // Severity mirrors the callee's: a static caller errors, an
                        // instance caller warns. Erroring unconditionally made a
                        // fixture whose refusals are all on instance methods exit
                        // non-zero, never reaching a verdict -- and it would contradict
                        // the callee, which only warns.
                        //
                        // Either way, name the callee: "a method could not be
                        // translated" left the user guessing which call was
                        // responsible, and the callee's own diagnostic is on another
                        // line (often another file).
                        reporter.compilationUnit = unit;
                        // Name the callee's OWN reason when we recorded one. Without it
                        // the caller is told only that some call "could not be
                        // translated", and the reason sits on another line -- often in
                        // another file -- so the user has to go hunting for it.
                        String because = "call to '" + callee.name + "', which could not be translated";
                        var calleeReasons = reportedInstanceRefusals.get(callee);
                        if (calleeReasons != null && calleeReasons.size() == 1) {
                            because += ": " + calleeReasons.iterator().next();
                        }
                        // Demotion runs for every detected call -- the soundness half.
                        // Only the DIAGNOSTIC is deduped: the caller is reported at
                        // its own declaration, so two refused callees in one body
                        // would stack identical messages on one position. The key
                        // includes the callee, so distinct callees each get a line.
                        if (firstReportOf(current.sym, because)) {
                            if ((current.mods.flags & Flags.STATIC) != 0) {
                                reporter.reportError(current, "translatorError", because);
                            } else {
                                reporter.reportDiagnostic(current,
                                        JCDiagnostic.DiagnosticType.WARNING,
                                        "instanceMethodNotVerified",
                                        current.name.toString(), because);
                            }
                        }
                        annotationCompiler.markSkipped(unit, current);
                    }
                    super.visitApply(tree);
                }
            });
        }
        return annotationCompiler.skippedMethodSymbols().size() > refusedBefore;
    }

    /**
     * Whether a skipped {@code callee} will still have a resolvable Laurel procedure,
     * so a caller of it need not be demoted. Exactly the condition the drop pass in
     * {@code analyzeJavaCode} decides by, read off the procedure already collected for
     * the symbol -- the two must agree, or a caller keeps a call to a procedure that
     * gets dropped and the program dies with `Resolution failed`.
     *
     * <p>A callee with no collected procedure ({@code proc == null}) is one whose own
     * translation threw before it was registered. Nothing is emitted for it either
     * way, so it cannot keep a declaration.
     */
    private boolean keepsDeclarationWhenSkipped(Symbol.MethodSymbol callee) {
        var proc = procedureBySymbol.get(callee);
        return jverifyUtils.isPure(callee) && proc != null && keepsOpaqueDeclaration(proc);
    }

    private boolean firstReportOf(Symbol.MethodSymbol sym, String reason) {
        return reportedInstanceRefusals
                .computeIfAbsent(sym, k -> new HashSet<>())
                .add(reason);
    }

    /**
     * True when a hard ERROR has already been reported at a position inside
     * {@code method} -- typically by {@code UnsupportedConstructValidator}, which
     * names the offending construct itself.
     *
     * <p>Used to suppress the {@code instanceMethodNotVerified} warning in that
     * case. The warning exists so a demoted method is not SILENT about why it was
     * not checked; when an error already points inside the same method the warning
     * is strictly less precise duplication (method-level, versus the construct's
     * own position), and the run exits non-zero regardless -- so dropping it cannot
     * hide an unchecked method behind a green run, which is the coupling the
     * demotion comment below protects. {@code markSkipped} stays unconditional, so
     * the method can still never be counted Verified.
     */
    private boolean hasReportedErrorInside(JCTree.JCMethodDecl method) {
        if (currentCompilationUnit == null) {
            return false;
        }
        int start = method.getStartPosition();
        int end = method.getEndPosition(currentCompilationUnit.endPositions);
        if (start < 0 || end < 0) {
            return false;
        }
        var source = currentCompilationUnit.getSourceFile();
        for (var diagnostic : reporter.diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR
                    || diagnostic.getSource() != source) {
                continue;
            }
            long position = diagnostic.getPosition();
            if (position < start || position > end) {
                continue;
            }
            // Containment alone is not enough: a nested or local class body also sits
            // inside this method's span, so an unrelated error in one of ITS methods
            // would suppress this method's warning and leave the demotion unexplained.
            // Require the error to belong to this method's own body, i.e. that the
            // innermost method declaration around it is this one.
            if (innermostMethodAt(position) == method) {
                return true;
            }
        }
        return false;
    }

    /// The innermost {@code JCMethodDecl} whose span contains {@code position}, or
    /// null. A nested class's method has a strictly smaller span than the outer
    /// method containing it, so the smallest match is the owner of the position.
    private JCTree.JCMethodDecl innermostMethodAt(long position) {
        var endPositions = currentCompilationUnit.endPositions;
        var best = new JCTree.JCMethodDecl[1];
        var bestWidth = new long[] {Long.MAX_VALUE};
        currentCompilationUnit.accept(new TreeScanner() {
            @Override
            public void visitMethodDef(JCTree.JCMethodDecl candidate) {
                int candidateStart = candidate.getStartPosition();
                int candidateEnd = candidate.getEndPosition(endPositions);
                if (candidateStart >= 0 && candidateEnd >= 0
                        && position >= candidateStart && position <= candidateEnd) {
                    long width = (long) candidateEnd - candidateStart;
                    if (width < bestWidth[0]) {
                        bestWidth[0] = width;
                        best[0] = candidate;
                    }
                }
                super.visitMethodDef(candidate);
            }
        });
        return best[0];
    }

    /// (helperName -> Procedure) for every cast helper referenced anywhere
    /// in the program. Lives at the whole-program scope (not per file)
    /// because all files share one Laurel program scope: emitting the
    /// same `java?cast_to_<T>_from_<S>` helper in two files makes Strata
    /// reject the duplicate. A LinkedHashMap keeps insertion order for
    /// deterministic output.
    private final java.util.LinkedHashMap<String, Procedure> castHelpers =
            new java.util.LinkedHashMap<>();

    /// Names of cast helpers already emitted in the output. Guards against
    /// re-emitting the same helper when we visit multiple compilation units:
    /// each helper is written into the first file that references it and
    /// skipped in subsequent files (same pattern as `emittedCompositeTypes`).
    private final Set<String> emittedCastHelpers = new HashSet<>();
    
    /// Fields to declare on each composite. Populated on-demand when a
    /// `JCFieldAccess` (read) or `JCAssign` with a `JCFieldAccess` LHS
    /// (write) is translated: only the specific field being accessed is
    /// added, not every field declared on the owning class -- see
    /// `ensureFieldOnComposite` for why walking every declared field
    /// breaks library-contract classes. Strata's
    /// `resolveQualifiedFieldName` requires each accessed field to be
    /// declared on its owning composite for heap parameterization to fire.
    /// Insertion order is preserved (both across composites and across
    /// fields within a composite) for deterministic output.
    /// Sort name -> instance procedures declared on it. Instance methods are emitted
    /// inside their composite (the backend lifts them), so they are collected across
    /// all compilation units and attached at emission.
    /// Symbols of methods opted out of verification (@Verify(false), resolved
    /// through the annotation stack). Snapshotted before any unit is translated:
    /// the overload gates also fire at CALL SITES, where a call in one unit can
    /// name a method in another, so taking it up front keeps refusals independent
    /// of translation order.
    private final Set<Symbol> optedOutMethods = new HashSet<>();

    private final Set<Symbol.MethodSymbol> calledStaticMethods = new HashSet<>();

    private final com.sun.tools.javac.code.Types javacTypes;

    private final Map<String, List<Procedure>> instanceProcsBySort = new LinkedHashMap<>();

    /// Method symbol -> the Procedure emitted for it. Lets the post-loop
    /// caller-drop remove a procedure by IDENTITY; matching on the mangled name
    /// would re-derive the static/instance naming rules and tie the drop to that
    /// spelling.
    private final Map<Symbol.MethodSymbol, Procedure> procedureBySymbol = new LinkedHashMap<>();

    private final Map<String, LinkedHashMap<String, Field>> compositeFields = new LinkedHashMap<>();

    /// Superclass links for composites that need their Java hierarchy modelled
    /// (today: exception types, whose `catch` guards and multi-`throws` joins
    /// depend on `extends` chains). Maps a
    /// composite sort name to its direct parent's sort name. Composites absent
    /// from this map are emitted with no `extending` clause, as before.
    private final Map<String, String> compositeExtends = new LinkedHashMap<>();

    /// The declared exceptional join of the method currently being translated
    /// (the class the `throws` clause resolves to, after the multi-throws LCA
    /// join), or null when it declares none. Set by `translateStaticMethod`;
    /// read by `convertStatement`'s `throw` case, which requires a declared
    /// exceptional exit and checks the thrown type fits under it.
    private Symbol.ClassSymbol currentThrowsJoin;

    /// The catch types of the `try` bodies enclosing the statement currently
    /// being converted, innermost first (`push` prepends; one entry per `try`,
    /// all arms flattened -- order is irrelevant to the `anyMatch` consumer).
    /// A `throw` whose type an enclosing arm catches needs no declared
    /// `throws` -- mirroring javac's own checked-exception rule.
    /// Scoped to the `try` BODY only: a throw inside a catch arm or finally
    /// block answers to the next try out, exactly as in Java.
    private final Deque<List<Symbol.ClassSymbol>> enclosingCatchTypes = new ArrayDeque<>();

    public JavaToLaurelCompiler(Context context) {
        symtab = com.sun.tools.javac.code.Symtab.instance(context);
        javacTypes = com.sun.tools.javac.code.Types.instance(context);
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        annotationCompiler = VerifyAnnotationCompiler.instance(context);
    }

    /** {@code entryProcedure}: the procedure the {@code --entry} method was emitted as, or null. */
    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap, String entryProcedure) {}

    // --- Helper methods for constructing AST nodes ---

    /** Get the best source tree for a statement — for expression statements,
     *  use the expression (excludes the semicolon). */
    private static JCTree sourceTreeFor(JCTree.JCStatement stmt) {
        if (stmt instanceof JCTree.JCExpressionStatement exprStmt) {
            return exprStmt.expr;
        }
        return stmt;
    }

    /**
     * Mirror of Strata's {@code FileRange.unknown} sentinel, for the nodes that
     * genuinely have no Java source: {@code AstNode.source} and
     * {@code Identifier.source} are non-optional on the Lean side, so every node
     * must carry *some* range. Prefer threading a real tree's range instead —
     * this sentinel is what Strata reports as "no location".
     */
    private static final FileRange UNKNOWN_RANGE =
            new FileRange(new Uri("<unknown>"), new SourceRange(new Raw(0), new Raw(0)));


    /**
     * Java allows {@code $} in identifiers (JLS 3.8); Laurel reserves a *leading*
     * {@code $} for compiler-generated names and rejects any source declaration that
     * starts with one. So a leading {@code $} that came from Java is escaped to
     * {@code @}: Java identifiers cannot contain {@code @} (JLS 3.8), which makes the
     * mapping injective -- the same property {@code MANGLE_SEPARATOR} and
     * {@code LAUREL_SELF_BINDING} rely on for {@code '?'}.
     *
     * <p>Only the first character is rewritten. A {@code $} further along a name is
     * legal in Laurel source, so {@code a$b} and javac's {@code val$x} capture fields
     * pass through as they are.
     *
     * <p>Nothing is exempt by spelling. Every name Strata dictates -- the prelude
     * operators and {@code $result} -- reaches Laurel through {@link #reservedIdent}
     * instead, so provenance decides, never the characters. Keying on the value would
     * mean a Java member genuinely named {@code $eq} or {@code $result} skipped escaping
     * and collided with the generated one.
     *
     * <p>Applied in {@code ident(...)}, the single place Laurel identifiers are built, so
     * a declaration and every reference to it are escaped alike -- escaping only one side
     * would turn a valid program into an unresolved name.
     */
    private static String laurelSafeName(String name) {
        return name.startsWith("$") ? "@" + name.substring(1) : name;
    }

    private static final String JVERIFY_MAP = "org.strata.jverify.JVerify.Map";
    private static final String JVERIFY_INT_MAP = "org.strata.jverify.JVerify.IntMap";

    /**
     * The Laurel type for a {@code JVerify.Map} type ARGUMENT.
     *
     * <p>A boxed primitive is refused. Unboxing it would yield a constrained integer, which Strata
     * does not accept as a generic datatype type argument ("constrained (subset) type 'int32' is
     * not yet supported"), and leaving it boxed strands the {@code Integer.valueOf} and
     * {@code intValue()} calls javac inserts around every use. Refusing here keeps the diagnostic
     * on the user's type argument and names the type that does work; {@code IntMap} covers the
     * int-to-int case that motivated the boxed spelling.
     */
    private HighType translateTypeArgument(com.sun.tools.javac.code.Type arg) {
        var unboxed = javacTypes.unboxedType(arg);
        if (unboxed != null && unboxed.isPrimitive()) {
            throw new JavaViolationException(
                    "JVerify.Map does not support the boxed primitive type argument " + arg
                            + "; use JVerify.IntMap for an int-to-int map");
        }
        return translateType(arg);
    }


    /**
     * A Laurel identifier for a name Strata itself dictates -- a prelude operator
     * procedure ({@code $add}, {@code $eq}, …) or the sole-output binding
     * {@code $result}. Never escaped, because JVerify chose it rather than reading it
     * out of the user's Java.
     *
     * <p>Distinguishing by provenance rather than spelling is the point: a user may
     * legally declare {@code int $eq;} or {@code int $result;}, and those must still be
     * escaped by {@link #ident} even though they collide with a dictated spelling.
     */
    private static Identifier reservedIdent(String name) {
        return new Identifier(name, Optional.empty(), UNKNOWN_RANGE);
    }

    /** {@link #var_} for a Strata-dictated name; see {@link #reservedIdent}. */
    private static StmtExpr reservedVar_(String name) {
        return new StmtExpr.Var(new Variable.Local(reservedIdent(name)));
    }

    private static Identifier ident(String name) {
        return new Identifier(laurelSafeName(name), Optional.empty(), UNKNOWN_RANGE);
    }

    /**
     * A named identifier carrying a source range. Strata locates some synthesized
     * conditions via the identifier naming their procedure -- the modifies frame in
     * ModifiesClauses.buildQuantifiedFrame takes proc.name.source -- so a range-less
     * procedure name is unattributable: reported at <unknown>(1:1) and dropped from
     * the per-method counts.
     */
    private Identifier ident(String name, JCTree tree) {
        return new Identifier(laurelSafeName(name), Optional.empty(), toFileRange(tree));
    }

    private FileRange toFileRange(JCTree tree) {
        if (tree == null || currentCompilationUnit == null) return UNKNOWN_RANGE;
        // `convertExpression` looks through parentheses, so a parenthesized tree
        // and its inner expression produce the same Laurel node. Stamp the inner
        // expression's range so a diagnostic on `((short) x) + 1` underlines
        // `(short) x` rather than including the redundant parentheses.
        if (tree instanceof JCTree.JCExpression exprTree) {
            tree = TreeInfo.skipParens(exprTree);
        }
        int startPos = TreeInfo.getStartPos(tree);
        int endPos = TreeInfo.getEndPos(tree, currentCompilationUnit.endPositions);
        if (endPos == -1) {
            // javac records no end position for some statement trees (a `for` loop
            // among them), and a zero-width range underlines nothing: the caret in
            // an IDE has no extent, and the test suite's `^^^` convention cannot
            // express width 0, so such a diagnostic cannot be pinned by a fixture.
            // Fall back to the first token at `startPos` -- for a `for` loop that is
            // the `for` keyword, which is what a reader expects the loop's implicit
            // obligations to underline.
            endPos = startPos + firstTokenLength(startPos);
        }
        var filePath = currentCompilationUnit.sourcefile.toUri().toString();
        return new FileRange(new Uri(filePath), new SourceRange(new Raw(startPos), new Raw(endPos)));
    }

    /**
     * Length of the identifier/keyword token starting at {@code pos} in the current
     * source, or 1 if the character there is not part of one. Gives a tree with no
     * recorded end position a range with real extent.
     */
    private int firstTokenLength(int pos) {
        CharSequence text;
        try {
            text = currentCompilationUnit.sourcefile.getCharContent(true);
        } catch (java.io.IOException e) {
            return 1;
        }
        if (pos < 0 || pos >= text.length()) return 1;
        int end = pos;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        return Math.max(1, end - pos);
    }

    private AstNode<StmtExpr> node(StmtExpr expr, JCTree tree) {
        return new AstNode<>(expr, toFileRange(tree));
    }

    private static AstNode<StmtExpr> node(StmtExpr expr) {
        return new AstNode<>(expr, UNKNOWN_RANGE);
    }

    private static AstNode<Variable> nodeVar(Variable v) {
        return new AstNode<>(v, UNKNOWN_RANGE);
    }

    private static AstNode<HighType> node(HighType type) {
        return new AstNode<>(type, UNKNOWN_RANGE);
    }

    private static AstNode<HighType> node(HighType type, FileRange range) {
        return new AstNode<>(type, range);
    }

    /**
     * Returns the source range of a type-syntax tree node (e.g. {@code method.restype},
     * {@code param.vartype}), or {@link #UNKNOWN_RANGE} if the node is null or synthetic
     * (negative positions).
     */
    private FileRange typeRangeOrNone(JCTree node) {
        if (node == null || currentCompilationUnit == null) return UNKNOWN_RANGE;
        int startPos = TreeInfo.getStartPos(node);
        if (startPos < 0) return UNKNOWN_RANGE;
        int endPos = TreeInfo.getEndPos(node, currentCompilationUnit.endPositions);
        if (endPos < 0) endPos = startPos;
        var filePath = currentCompilationUnit.sourcefile.toUri().toString();
        return new FileRange(new Uri(filePath), new SourceRange(new Raw(startPos), new Raw(endPos)));
    }

    /**
     * Java mirror of Strata's {@code Operation.procName}
     * ({@code Strata/Languages/Laurel/LaurelAST.lean}). Operators are no longer a
     * distinct expression kind: {@code StmtExpr.PrimitiveOp} was folded into
     * {@code StaticCall}, so {@code x + y} is a call to the overloaded built-in
     * wrapper {@code $add} that Laurel prepends to every program.
     *
     * <p>Must stay in sync with the Lean table: an unmapped operator would emit a
     * call to a procedure that does not exist and fail Strata's resolver.
     */
    private static String operationProcName(Operation op) {
        return switch (op) {
            case Operation.Eq _ -> "$eq";
            case Operation.Neq _ -> "$neq";
            case Operation.And _ -> "$and";
            case Operation.Or _ -> "$or";
            case Operation.Not _ -> "$not";
            case Operation.Implies _ -> "$implies";
            case Operation.AndThen _ -> "$andThen";
            case Operation.OrElse _ -> "$orElse";
            case Operation.Neg _ -> "$neg";
            case Operation.Add _ -> "$add";
            case Operation.Sub _ -> "$sub";
            case Operation.Mul _ -> "$mul";
            case Operation.Div _ -> "$div";
            case Operation.Mod _ -> "$mod";
            case Operation.DivT _ -> "$divT";
            case Operation.ModT _ -> "$modT";
            case Operation.Lt _ -> "$lt";
            case Operation.Leq _ -> "$le";
            case Operation.Gt _ -> "$gt";
            case Operation.Geq _ -> "$ge";
            case Operation.StrConcat _ -> "$strConcat";
        };
    }

    private static StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return new StmtExpr.StaticCall(reservedIdent(operationProcName(op)), List.of(args), List.of());
    }

    /**
     * A Laurel identifier for a local name that may have been renamed by the
     * postcondition-lambda rewrite. A rename target is a Strata-dictated name
     * ({@code $result}) that JVerify chose, so it takes the {@link #reservedIdent} path;
     * an unrenamed name came from the user's Java and is escaped by {@link #ident}.
     */
    private static Identifier identForPossiblyRenamed(String name, Map<String, String> renames) {
        String renamed = renames.get(name);
        return renamed != null ? reservedIdent(renamed) : ident(name);
    }

    /** {@link #identForPossiblyRenamed} in expression position. */
    private static StmtExpr varForPossiblyRenamed(String name, Map<String, String> renames) {
        return new StmtExpr.Var(new Variable.Local(identForPossiblyRenamed(name, renames)));
    }

    private static StmtExpr var_(String name) {
        return new StmtExpr.Var(new Variable.Local(ident(name)));
    }

    private static StmtExpr varDecl(String name, HighType type, StmtExpr init) {
        // `Variable.Declare` carries a `ParameterOpt` (Lean `Parameter?`), whose
        // type annotation is optional; jverify always knows the declared type,
        // so it is always present here.
        var param = new ParameterOpt(ident(name), Optional.of(node(type)));
        var decl = new Variable.Declare(param);
        if (init == null) {
            return new StmtExpr.Var(decl);
        }
        return new StmtExpr.Assign(List.of(nodeVar(decl)), node(init));
    }

    // --- Main analysis entry point ---

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        if (loweredResult == null) return null;

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();
        optedOutMethods.addAll(annotationCompiler.skippedMethodSymbols());
        for (var unit : loweredResult.parsed()) {
            if (unit.sourcefile != null) {
                ownSourceFiles.add(unit.sourcefile);
            }
        }
        boolean first = true;
        for (var compilationUnit : loweredResult.parsed()) {
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            currentCompilationUnit = compilationUnit;
            reporter.compilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);

            List<TypeDefinition> types = new ArrayList<>();
            List<Procedure> procs = new ArrayList<>();

            if (first) {
                types.addAll(getPredefinedTypes());
                first = false;
            }
            // Composites are NOT emitted here: they are collected across all
            // compilation units and emitted in a post-pass below, so that a
            // field access discovered in a later unit still lands in its
            // composite's field list.
            //
            // Emit cast helper procedures (see convertTypeCast) before the
            // procedures that call them, so Strata's resolver can find them.
            // Skip helpers already emitted in an earlier file -- all Laurel
            // files share one program scope, so a repeated helper would be
            // a duplicate definition.
            for (var entry : castHelpers.entrySet()) {
                if (emittedCastHelpers.add(entry.getKey())) {
                    procs.add(entry.getValue());
                }
            }
            procs.addAll(visitor.procedures);

            var program = new Program(procs, List.of(), types, List.of());
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(), program));
            lineMaps.put(compilationUnit.sourcefile.toUri(), compilationUnit.getLineMap());
        }
        emitCalledContractStatics(loweredResult, result, lineMaps);

        // A method that CALLS one refused during translation cannot be emitted
        // either: its call names an undeclared Laurel procedure, surfacing as
        // `Resolution failed: 'Pkg.Class?static?callee' is not defined` -- an internal
        // mangled symbol the user never wrote. Blame the caller at its own call site,
        // and count it Skipped so methodsSkipped stays honest.
        //
        // After the loop, so every unit's refusals are known, which is what the
        // cross-unit case needs -- the same reason composites are emitted below rather
        // than per unit. An opted-out callee is caught at the call site before the
        // caller is translated; this scan handles the callee whose own TRANSLATION
        // failed, unknowable until it is attempted. Iterated to a FIXPOINT. A -> B -> C with C refused demotes B on the first
        // round; without a second round A stays translated while B's procedure is
        // dropped below, so A's body calls an unemitted callee and the program dies
        // with `Resolution failed: '...?static?B' is not defined` -- the unattributable
        // failure this scan exists to prevent, one level deeper (CascadeDepth3 pins it).
        //
        // `skippedMethodSymbols()` RECOMPUTES from `methodStatusPerUri`, so re-reading it
        // each round is the authoritative view and `markSkipped` is the only writer. That
        // is deliberate: the original defect was exactly one snapshot of this set going
        // stale against the live `isSkipped` the drop pass below reads, so keeping a
        // second, hand-synchronised copy here would reintroduce the same divergence.
        // Re-read per ROUND rather than per method, so a round sees one consistent view
        // and the diagnostic order does not depend on traversal position.
        //
        // Terminates because the skipped set only grows and is bounded by the method
        // count; a demotion cycle (A and B calling each other, one refused) is fine --
        // the `!contains(current.sym)` guard stops an already-refused method being
        // re-examined. Round count is bounded by the longest refused call chain, which is
        // shallow in practice, so re-walking the trees per round costs nothing measurable
        // beside verification; a worklist over a precomputed reverse call graph would be
        // asymptotically better but needs a Symbol -> (unit, decl) map, which keying on
        // symbols exists to avoid. Two things need no per-round handling: severity keys on
        // the CALLER's own static-ness, not the chain's origin, and re-reporting is
        // prevented by `firstReportOf(current.sym, because)`, whose key includes the callee.
        //
        // A narrow call scan rather than re-running the collector: re-translating
        // re-reports every diagnostic (measured -- one fault produced two identical
        // errors) and Reporter has no suppression hook. Keyed on SYMBOLS via
        // `skippedMethodSymbols()`, so no symbol-to-tree lookup is needed.
        while (demoteCallersOfRefusedMethods(loweredResult)) {
            // Repeat until a round demotes nothing new: a refused C demotes B, and only
            // the next round can see B in order to demote A.
        }

        // Drop the procedures for methods the scan above just skipped. `markSkipped`
        // fixes only the ACCOUNTING; the procedure was already collected during the
        // loop, and leaving it in keeps the call to the unemitted callee -- the
        // `Resolution failed: '...?static?callee' is not defined` above.
        //
        // `Program` is an immutable record, so each file is rebuilt rather than
        // mutated, as in the composite post-pass below.
        // A skipped PURE method keeps a DECLARATION instead of being dropped: same
        // signature and preconditions, but an opaque body with no implementation and
        // no postconditions. That is sound -- a pure method has an empty frame, and
        // exposing no postcondition means a caller learns nothing about the result --
        // and it keeps the callee's name resolvable, so callers no longer cascade into
        // a refusal just because one callee could not be translated
        // (see PureUnverifiedMethod, whose `@Verify(false) @Pure` callee is meant to be
        // callable-but-invisible).
        //
        // Only pure methods qualify: there is no `modifies *` in the Laurel bindings,
        // so a non-pure callee cannot be given a frame that is honest about what it
        // might write, and dropping it (plus demoting its callers) stays the sound
        // treatment.
        //
        // Purity alone is not enough, though -- `keepsOpaqueDeclaration` is the
        // condition, and it is the same one `demoteCallersOfRefusedMethods` exempts a
        // callee by, so a procedure dropped here has already had its callers demoted.
        var droppedProcedures = new HashSet<Procedure>();
        var opaqueReplacements = new HashMap<Procedure, Procedure>();
        for (var entry : procedureBySymbol.entrySet()) {
            var decl = entry.getKey();
            for (var unit : loweredResult.parsed()) {
                var tree = TreeInfo.declarationFor(decl, unit);
                if (tree instanceof JCTree.JCMethodDecl md
                        && annotationCompiler.isSkipped(unit, md)) {
                    if (jverifyUtils.isPure(decl) && keepsOpaqueDeclaration(entry.getValue())) {
                        opaqueReplacements.put(entry.getValue(), declarationOnly(entry.getValue()));
                    } else {
                        droppedProcedures.add(entry.getValue());
                    }
                    break;
                }
            }
        }
        if (!opaqueReplacements.isEmpty()) {
            for (int i = 0; i < result.size(); i++) {
                var file = result.get(i);
                var prog = file.program();
                boolean touched = prog.staticProcedures().stream().anyMatch(opaqueReplacements::containsKey);
                if (touched) {
                    var mapped = prog.staticProcedures().stream()
                            .map(p -> opaqueReplacements.getOrDefault(p, p)).toList();
                    result.set(i, new LaurelFile(file.uri(), new Program(
                            mapped, prog.staticFields(), prog.types(), prog.constants())));
                }
            }
            instanceProcsBySort.replaceAll((sort, procs) ->
                    procs.stream().map(p -> opaqueReplacements.getOrDefault(p, p)).toList());
        }
        if (!droppedProcedures.isEmpty()) {
            for (int i = 0; i < result.size(); i++) {
                var file = result.get(i);
                var prog = file.program();
                var kept = prog.staticProcedures().stream()
                        .filter(p -> !droppedProcedures.contains(p)).toList();
                if (kept.size() != prog.staticProcedures().size()) {
                    result.set(i, new LaurelFile(file.uri(), new Program(
                            kept, prog.staticFields(), prog.types(), prog.constants())));
                }
            }
            instanceProcsBySort.replaceAll((sort, procs) ->
                    procs.stream().filter(p -> !droppedProcedures.contains(p)).toList());
        }

        // Emit composite declarations after ALL compilation units have been
        // visited, so that field accesses discovered in later units are
        // included in the composite's field list. Composites are inserted into
        // the first file's type list, after the predefined types, so Strata's
        // resolver can find them before any procedure that references them.
        //
        // `Program` is an immutable record, so the first file is rebuilt rather
        // than mutated in place.
        if (!result.isEmpty()) {
            List<TypeDefinition> composites = new ArrayList<>();
            Set<String> emitted = new HashSet<>();
            for (var entry : new ArrayList<>(referencedCompositeTypes.entrySet())) {
                emitComposite(entry.getValue(), entry.getKey(), emitted, composites);
            }
            var firstFile = result.getFirst();
            var firstProgram = firstFile.program();
            List<TypeDefinition> mergedTypes = new ArrayList<>(firstProgram.types());
            mergedTypes.addAll(getPredefinedTypes().size(), composites);
            result.set(0, new LaurelFile(firstFile.uri(), new Program(
                    firstProgram.staticProcedures(), firstProgram.staticFields(),
                    mergedTypes, firstProgram.constants())));
        }

        FilesMap filesMap = (uri, offset) -> {
            var lineMap = lineMaps.get(uri);
            if (lineMap == null) {
                // jverify threads a source range from the generating Java tree
                // into every synthesized clause/binding, so a diagnostic should
                // normally carry a real user-source location. If one still
                // arrives against Strata's synthetic "<unknown>" path, some
                // synthesis site failed to thread its source -- a jverify bug.
                // Surface it LOUDLY but do not crash: a 1:1 fallback keeps the
                // underlying Strata diagnostic visible instead of masking it.
                // Any OTHER unmapped URI indicates a real line-map collection
                // bug; keep failing fast there.
                String path = uri.getPath();
                if (path != null && path.endsWith(SYNTHETIC_UNKNOWN_PATH)) {
                    String message = "a Strata diagnostic was reported against a source-less "
                            + "synthesized node (" + uri + "); a synthesis pass did not thread a "
                            + "source range from its generating context.";
                    // Under test, FAIL: such a diagnostic is invisible to the user
                    // (reported at <unknown>(1:1) and dropped from the per-method
                    // counts), so it must not reach a release. In production stay
                    // non-fatal -- the 1:1 fallback keeps the underlying Strata
                    // diagnostic visible rather than masking it.
                    if (Boolean.getBoolean(STRICT_SOURCE_RANGES_PROPERTY)) {
                        throw new IllegalStateException("[jverify] internal: " + message
                                + " Set -D" + STRICT_SOURCE_RANGES_PROPERTY + "=false to downgrade "
                                + "this to a warning.");
                    }
                    System.err.println("[jverify] internal: " + message
                            + " Falling back to 1:1 -- please report this as a jverify bug.");
                    return new Position(1, 1);
                }
                throw new RuntimeException("Could not find line map for " + uri);
            }
            long line = lineMap.getLineNumber(offset);
            long lineStart = lineMap.getStartPosition(line);
            long column = offset - lineStart;
            return new Position((int)line, (int)column + 1);
        };

        var entry = jverifyUtils.entry() == null ? null : procedureBySymbol.get(jverifyUtils.entry());
        return new AnalysisResult(result, filesMap,
                entry == null || droppedProcedures.contains(entry) ? null : entry.name().text());
    }

    /// Bounds for each built-in constrained integer type. Single source of
    /// truth: `getPredefinedTypes()` emits the constrained-type prelude from
    /// this map, and `castHelperBoundsFor(name)` reads back the same bounds
    /// when synthesising a cast helper's `requires` clause -- otherwise the
    /// cast's range check could drift from the target type's own constraint
    /// (a soundness-critical invariant).
    ///
    /// Value: `{min, max}`; a `null` element means "unbounded on that side"
    /// (e.g. `nat` has no upper bound). Insertion order is preserved so the
    /// emitted prelude order stays deterministic.
    ///
    /// Nat majors' upper bound matches the signed type of the same width,
    /// so a `@Nat int` (nat31) ranges over 0..2^31-1.
    private static final java.util.LinkedHashMap<String, Long[]> BOUNDS = new java.util.LinkedHashMap<>();
    static {
        BOUNDS.put("int8",  new Long[]{-128L, 127L});
        BOUNDS.put("int16", new Long[]{-32768L, 32767L});
        BOUNDS.put("int32", new Long[]{-2147483648L, 2147483647L});
        BOUNDS.put("int64", new Long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        BOUNDS.put("char",  new Long[]{0L, 65535L});
        // @Nat counterparts: >= 0, upper bound same as the signed counterpart.
        BOUNDS.put("nat7",  new Long[]{0L, 127L});
        BOUNDS.put("nat15", new Long[]{0L, 32767L});
        BOUNDS.put("nat31", new Long[]{0L, 2147483647L});
        BOUNDS.put("nat63", new Long[]{0L, Long.MAX_VALUE});
        // @Unbounded @Nat: >= 0 with no upper bound.
        BOUNDS.put("nat",   new Long[]{0L, null});
    }

    private List<TypeDefinition> getPredefinedTypes() {
        var types = new ArrayList<TypeDefinition>();
        for (var entry : BOUNDS.entrySet()) {
            types.add(makeConstrainedType(entry.getKey(),
                    entry.getValue()[0], entry.getValue()[1]));
        }
        return types;
    }

    private TypeDefinition makeConstrainedType(String name, Long min, Long max) {
        var x = var_("x");
        var bounds = new ArrayList<StmtExpr>();
        if (min != null) {
            bounds.add(primitiveOp(new Operation.Geq(), node(x), node(longLiteral(min))));
        }
        if (max != null) {
            bounds.add(primitiveOp(new Operation.Leq(), node(x), node(longLiteral(max))));
        }
        var constraint = bounds.stream()
                .reduce((a, b) -> primitiveOp(new Operation.And(), node(a), node(b)))
                .orElse(new StmtExpr.LiteralBool(true));
        var witness = new StmtExpr.LiteralInt(0);
        return new TypeDefinition.Constrained(new ConstrainedType(
                ident(name), node(new HighType.TInt()), ident("x"), node(constraint), node(witness)));
    }

    private static StmtExpr longLiteral(long val) {
        if (val >= 0) return new StmtExpr.LiteralInt(val);
        // LiteralInt serializes via ion.newInt(long) which handles negative values directly.
        // However, the Lean-side deserializer may expect Neg(LiteralInt(posVal)) for negative
        // numbers. For Long.MIN_VALUE, -val overflows, so emit the raw negative literal.
        if (val == Long.MIN_VALUE) {
            return new StmtExpr.LiteralInt(val);
        }
        return primitiveOp(new Operation.Neg(), node(new StmtExpr.LiteralInt(-val)));
    }

    private HighType translateType(com.sun.tools.javac.code.Type type) {
        return translateType(type, null);
    }

    /**
     * Translate a javac type to a Laurel HighType, wrapping it in an AstNode
     * stamped with the given source range from the declared type tree.
     *
     * <p>A resolved {@link com.sun.tools.javac.code.Type} is a symbol-table entity with
     * no tree position, so the caller must supply the range of the declared type's syntax
     * tree (e.g. {@code method.restype}, {@code varDecl.vartype}) where one exists. An
     * empty range serializes as a null source, which Strata treats as "no location" and
     * can only report at a whole-file fallback position.
     */
    private AstNode<HighType> translateTypeWithRange(com.sun.tools.javac.code.Type type,
                                                     JCTree.JCModifiers modifiers,
                                                     FileRange range) {
        return node(translateType(type, modifiers), range);
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
    private HighType translateType(com.sun.tools.javac.code.Type type, JCTree.JCModifiers modifiers) {
        // A wildcard type argument is approximated by the type a READ through it
        // yields, since Laurel has no variance. `? extends X` reads as `X` (its
        // upper bound); `? super X` reads only as `java.lang.Object` (the actual
        // could be `Container<Object>`), so it maps to Object -- NOT to `X`, which
        // would be unsound; and an unbounded `?` is Object too. A FOR-NOW
        // approximation of full variance (STRATA_WILDCARDS): sound, but coarser
        // than a variance-aware check.
        if (type instanceof com.sun.tools.javac.code.Type.WildcardType wildcard) {
            com.sun.tools.javac.code.Type bound =
                    (wildcard.isExtendsBound() && wildcard.type != null)
                            ? wildcard.type
                            : symtab.objectType;
            return translateType(bound, modifiers);
        }
        // Class / interface types (including sealed hierarchies and
        // records): encode as an opaque Laurel composite type named
        // after the source-side type. Strata treats unbound composite
        // types as uninterpreted reference sorts, which is enough
        // for parameter-position acceptance (e.g.
        // `static void leftIdentityNone(PathLengthRange r)`).
        // Body-level operations on the value (instanceof, pattern
        // match, record-component reads, constructors) need
        // additional translation that is NOT part of this commit;
        // they will surface as separate convertExpression errors.
        // A type variable declared by the method being translated becomes a
        // Laurel type variable, declared in the procedure's typeArgs. Strata
        // monomorphizes the procedure per call site and infers the type
        // arguments there, so no call-site change is needed. The BOUND is
        // dropped -- Laurel's TVar is a gradual wildcard -- so a body that
        // actually uses the bound's members still refuses, exactly as before.
        // A type variable becomes a Laurel TVar. A METHOD's own parameter is
        // declared in the procedure's typeArgs; a CLASS's parameter is declared
        // in the owning composite's typeArgs (emitComposite). The BOUND is dropped
        // (Laurel TVar is unconstrained), so a body that uses the bound's members
        // still fails (STRATA_NO_TYPE_CONSTRAINTS).
        if (type instanceof com.sun.tools.javac.code.Type.TypeVar typeVar
                && (typeVar.tsym.owner instanceof Symbol.MethodSymbol
                        || typeVar.tsym.owner instanceof Symbol.ClassSymbol)) {
            return new HighType.TVar(ident(typeVar.tsym.name.toString()));
        }
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            // `IntMap`'s key and value are the UNCONSTRAINED Laurel `int`, not `int32`: a
            // constrained output demotes the prelude's transparent `select`/`mapGet` body to
            // opaque (STRATA_CONSTRAINED_OUTPUT_TRANSPARENCY), losing exactly the
            // read-is-a-function property this type exists to provide. Sound because the type
            // is verification-only and never holds a runtime value.
            if (JVERIFY_INT_MAP.equals(classType.tsym.getQualifiedName().toString())) {
                return new HighType.TMap(node(new HighType.TInt()), node(new HighType.TInt()));
            }
            // `JVerify.Map<K, V>` is Laurel's PARTIAL map (`Map<K, V>` aliases
            // `TotalMap K ($MapEntry<V>)`), not an opaque composite: an uninterpreted sort has
            // no `mapGet` to resolve against. `HighType.TMap` is the TOTAL map, so the partial
            // one has to be named through its alias.
            if (JVERIFY_MAP.equals(classType.tsym.getQualifiedName().toString())) {
                var mapArgs = classType.getTypeArguments();
                if (mapArgs == null || mapArgs.size() != 2) {
                    throw new JavaViolationException(
                            "JVerify.Map needs both type arguments: " + type);
                }
                return new HighType.Applied(node(new HighType.UserDefined(ident("Map"))),
                        List.of(node(translateTypeArgument(mapArgs.get(0))),
                                node(translateTypeArgument(mapArgs.get(1)))));
            }
            String sortName = compositeSortName(classType.tsym);
            referencedCompositeTypes.putIfAbsent(sortName, classType.tsym);
            // A generic INSTANTIATION of a MODELED composite
            // (`GenericContainer<DummySuper>`) is an `Applied` of the base sort to
            // its translated type arguments; Strata monomorphizes it. A raw use, a
            // non-generic type, or a library generic without a @Contract stays a
            // bare erased sort -- no definition is emitted for it, so an Applied
            // over it would not resolve.
            var typeArgs = classType.getTypeArguments();
            if (typeArgs != null && !typeArgs.isEmpty() && isModeledComposite(classType.tsym)) {
                List<AstNode<HighType>> applied = new ArrayList<>();
                for (var arg : typeArgs) {
                    applied.add(node(translateType(arg)));
                }
                return new HighType.Applied(
                        node(new HighType.UserDefined(ident(sortName))), applied);
            }
            return new HighType.UserDefined(ident(sortName));
        }
        boolean isNat = JVerifyUtils.isAnnotated(type, org.strata.jverify.Nat.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Nat.class);
        boolean isUnbounded = JVerifyUtils.isAnnotated(type, org.strata.jverify.Unbounded.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Unbounded.class);

        return switch (type.getTag()) {
            case INT -> integerType(isNat, isUnbounded, "int32", "nat31");
            case SHORT -> integerType(isNat, isUnbounded, "int16", "nat15");
            case BYTE -> integerType(isNat, isUnbounded, "int8", "nat7");
            case LONG -> integerType(isNat, isUnbounded, "int64", "nat63");
            case CHAR -> new HighType.UserDefined(ident("char"));
            case BOOLEAN -> new HighType.TBool();
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    /**
     * Pick the Laurel type for an integral Java type given its {@code @Nat}/{@code @Unbounded}
     * annotations. {@code bounded}/{@code boundedNat} are the fixed-width type names for the
     * default and {@code @Nat} cases; {@code @Unbounded} maps to the unbounded {@code int}
     * (or {@code nat} when also {@code @Nat}).
     */
    private HighType integerType(boolean isNat, boolean isUnbounded, String bounded, String boundedNat) {
        if (isUnbounded) {
            return isNat ? new HighType.UserDefined(ident("nat")) : new HighType.TInt();
        }
        return new HighType.UserDefined(ident(isNat ? boundedNat : bounded));
    }

    /**
     * Sort name used to declare {@code cls} as a Laurel composite. Fully qualified with
     * the package included and the nested-class {@code $} separator normalised to
     * {@code .}, matching how {@link #translateType} names class-typed sorts.
     */
    /**
     * The enclosing {@link Symbol.ClassSymbol} of {@code sym}, or refuse if there
     * is none. {@code enclClass()} walks the owner chain casting to ClassSymbol;
     * some callees (record accessors of types in an anonymous class, built-ins on
     * synthetic Symtab entries) have a non-ClassSymbol owner and make it return null
     * or throw ClassCastException. Callers that dereference the result must route
     * through here, turning such a symbol into a graceful per-method skip instead of
     * an uncaught crash aborting the whole source file.
     */
    /**
     * Refuse a user binder spelled like a Strata-reserved name. Strata's `: T`
     * return form generates an output parameter named {@code $result} for every
     * returning procedure, so a user binder of that name collides inside Strata
     * ("Variable $result of type int already in context") and aborts the backend
     * with no attributable location. '$' is a legal Java identifier character
     * (JLS 3.8), so no spelling of our own binder avoids the collision, unlike the
     * '?' in {@link #LAUREL_SELF_BINDING}. Refuse for a graceful skip with a
     * diagnostic pointing at the offending binder.
     */
    /**
     * Refuse an object allocation inside a contract expression. Returns
     * {@link StmtExpr} so it can stand in expression position; always throws.
     */
    private static StmtExpr refuseAllocationInContract() {
        throw new JavaViolationException(
                "object allocation (including lambdas) inside a contract is not yet supported");
    }

    private static void refuseReservedName(Name name) {
        if (name.contentEquals(LAUREL_RESULT_BINDING)) {
            throw new JavaViolationException(
                    "identifier '" + LAUREL_RESULT_BINDING + "' is reserved by the verifier");
        }
    }

    static Symbol.ClassSymbol enclosingClassOrRefuse(Symbol.MethodSymbol sym) {
        Symbol.ClassSymbol enclosing;
        try {
            enclosing = sym.enclClass();
        } catch (ClassCastException e) {
            enclosing = null;
        }
        if (enclosing == null) {
            throw new JavaViolationException(
                    "method '" + sym.name + "' has no enclosing class symbol (not yet supported)");
        }
        return enclosing;
    }

    /// Separator between the enclosing-class name and the method name in a
    /// mangled procedure name. '?' cannot appear in a Java identifier (JLS 3.8), so
    /// the join is injective where a '_' join is not: `Outer.Inner?foo` cannot be
    /// forged, but `Outer.Inner_foo` is also how a method `Inner_foo` on `Outer` would
    /// mangle. Not reachable for statics -- those are relocated into per-class
    /// `<Class>?static` symbols first, so two never share an enclosing class -- but
    /// instance methods keep theirs. Same convention as MoveStaticMethodsToStaticType.
    private static final char MANGLE_SEPARATOR = '?';

    /**
     * Declare the composite sort {@code name} with its Java supertype closure,
     * ANCESTORS FIRST. Every sort {@code extends} its direct supertypes (superclass
     * + all interfaces, including {@code java.lang.Object}), and Strata's resolver
     * walks those edges transitively, so a value flows to any supertype-typed
     * position exactly as Java's subtyping allows. Lambdas and method references
     * need no special case: their synthetic class carries its functional interface
     * on {@code interfaces_field}, which {@code getInterfaces()} returns.
     *
     * <p>Ancestors-first is REQUIRED. Laurel builds a composite's field scope by
     * copying each parent's scope when the child is resolved, so a parent declared
     * AFTER its child leaves inherited fields undefined ("'field' is not defined").
     * Reference order, which {@code referencedCompositeTypes} preserves, is not
     * topological, so the recursion is what makes the emitted order safe.
     *
     * <p>{@code emitted} dedups across the whole program, not just within a file:
     * all Laurel files share one scope, so a repeated declaration is a duplicate
     * definition.
     */
    private void emitComposite(Symbol.TypeSymbol tsym, String name,
            Set<String> emitted, List<TypeDefinition> out) {
        if (!emitted.add(name)) {
            return;
        }
        // `extending` widened to `List<AstNode<HighType>>` by the poly grammar: a
        // composite may extend a generic instantiation. Java has no generics surface
        // here, so every parent is a plain nominal type wrapped as `HighType.UserDefined`.
        List<AstNode<HighType>> parents = new ArrayList<>();
        if (tsym instanceof Symbol.ClassSymbol cls && tsym.type != null) {
            com.sun.tools.javac.code.Type sup = cls.getSuperclass();
            if (sup != null && sup.tsym != null && sup.getTag() != TypeTag.NONE) {
                addParent(sup, name, parents, emitted, out);
            }
            for (var iface : cls.getInterfaces()) {
                if (iface.tsym != null) {
                    addParent(iface, name, parents, emitted, out);
                }
            }
        }
        if (parents.isEmpty() && !OBJECT_SORT.equals(name)) {
            // Roots other than Object itself still have Object as a supertype:
            // interfaces (getSuperclass() returns noType, but JLS 4.10.2 makes Object
            // a direct supertype of a superinterface-less interface) and synthetic
            // lambda classes (supertype field never set). Without this edge such a
            // value cannot flow to an Object-typed position.
            addParent(symtab.objectType.tsym.type, name, parents, emitted, out);
        }
        List<Field> fields = compositeFields.containsKey(name)
                ? new ArrayList<>(compositeFields.get(name).values())
                : List.of();
        // A modeled generic class declares its type parameters so a `TVar` naming
        // one resolves against the composite; its instantiations monomorphize per
        // `Applied` use site.
        List<Identifier> typeArgs = new ArrayList<>();
        if (tsym.type != null && isModeledComposite(tsym)) {
            for (var tv : tsym.type.getTypeArguments()) {
                if (tv.tsym != null) {
                    typeArgs.add(ident(tv.tsym.name.toString()));
                }
            }
        }
        out.add(new TypeDefinition.Composite(new CompositeType(ident(name),
                typeArgs, parents, fields,
                instanceProcsBySort.getOrDefault(name, List.of()))));
    }

    /** Emit {@code parentSym}'s sort (and its ancestors) and name it as a parent of
     *  the sort being declared, unless it is that sort itself. */
    private void addParent(com.sun.tools.javac.code.Type parentType, String childName,
            List<AstNode<HighType>> parents, Set<String> emitted, List<TypeDefinition> out) {
        Symbol.TypeSymbol parentSym = parentType.tsym;
        String parent = compositeSortName(parentSym);
        if (parent.isEmpty() || parent.equals(childName)) {
            return;
        }
        // Emit the base composite (by symbol, ancestors-first), but make the EDGE
        // carry the parameterized supertype: a class or lambda implementing a
        // generic MODELED interface must extend the instantiation
        // (`Applied(MyConsumer, [Anything])`) to match an `Applied`-typed use site.
        // translateType erases a library or non-generic supertype to a bare sort.
        emitComposite(parentSym, parent, emitted, out);
        parents.add(node(translateType(parentType)));
    }

    /**
     * True for a composite whose DEFINITION this compilation emits -- so its type
     * parameters are declared and Strata can monomorphize it: a type this
     * compilation has source for, or a library type given a {@code @Contract}
     * (ExternalContractCompiler moves the annotation onto the contractee, which is
     * then emitted as a real composite). A plain library generic with neither
     * ({@code Supplier}, {@code HashMap}) stays erased to a bare sort.
     */
    private boolean isModeledComposite(Symbol.TypeSymbol tsym) {
        if (!(tsym instanceof Symbol.ClassSymbol cls)) {
            return false;
        }
        return (cls.sourcefile != null && ownSourceFiles.contains(cls.sourcefile))
                || JVerifyUtils.isAnnotated(cls.type, org.strata.jverify.Contract.class);
    }

    private static String compositeSortName(Symbol.TypeSymbol tsym) {
        var name = tsym.getQualifiedName();
        if (name.isEmpty() && tsym instanceof Symbol.ClassSymbol cls) {
            // Anonymous and local classes have an EMPTY qualified name, so without
            // this fallback they all share one empty-named sort: only the first
            // declaration is emitted and every later class collides with it. The
            // flatname (`pkg.Outer$1`) is unique per class.
            name = cls.flatName();
        }
        return name.toString().replace('$', '.');
    }

    /** A translated {@code throws} clause: the join class and its Laurel type node. */
    private record DeclaredThrows(Symbol.ClassSymbol join, AstNode<HighType> type) {}

    /**
     * Translate a method's Java {@code throws} clause into the declared Laurel
     * exceptional type. Laurel declares at most
     * one exceptional exit, so {@code throws E1, E2} joins to the least common
     * ancestor of the thrown classes. Each thrown class is declared as a
     * composite with its superclass chain linked all the way up to
     * {@code Throwable}, not merely up to the join: a <em>caller</em> may
     * catch any supertype of the declared type ({@code catch (Exception e)}
     * around a call declaring {@code throws AppException}), and that guard's
     * {@code is} test needs the chain between the two types linked no matter
     * where they sit. Chains scoped to the join would leave the sorts
     * unrelated and the handler dead.
     *
     * <p>The join can silently widen: unrelated thrown types join at a broad
     * ancestor (up to {@code Exception}/{@code Throwable}). Precision is
     * recoverable per case via the contract DSL's type claims (phase 4); a
     * widening lint is deliberately deferred until that DSL exists to state
     * the precise set.
     */
    private DeclaredThrows translateThrowsClause(
            com.sun.tools.javac.util.List<JCTree.JCExpression> thrownTrees) {
        List<Symbol.ClassSymbol> thrown = new ArrayList<>();
        for (var tree : thrownTrees) {
            if (!(tree.type instanceof com.sun.tools.javac.code.Type.ClassType ct)
                    || !(ct.tsym instanceof Symbol.ClassSymbol cls)) {
                // e.g. a generic exception parameter: `<X extends Exception> ... throws X`.
                throw new JavaViolationException(
                        "Unsupported thrown type in throws clause: " + tree);
            }
            thrown.add(cls);
        }
        Symbol.ClassSymbol join = thrown.getFirst();
        for (var cls : thrown.subList(1, thrown.size())) {
            join = leastCommonAncestor(cls, join);
        }
        for (var cls : thrown) {
            registerHierarchyUpToThrowable(cls);
        }
        String joinSort = compositeSortName(join);
        referencedCompositeTypes.putIfAbsent(joinSort, join);
        return new DeclaredThrows(join,
                node(new HighType.UserDefined(ident(joinSort)),
                        typeRangeOrNone(thrownTrees.getFirst())));
    }

    /** True iff {@code cls} has {@code java.lang.Throwable} on its superclass chain. */
    private static boolean isThrowableSubtype(Symbol.ClassSymbol cls) {
        for (Symbol.ClassSymbol c = cls; c != null; c = superclassOf(c)) {
            if (c.getQualifiedName().contentEquals("java.lang.Throwable")) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code cls} is {@code ancestor} or below it on the superclass chain. */
    private static boolean isSubclassOf(Symbol.ClassSymbol cls, Symbol.ClassSymbol ancestor) {
        for (Symbol.ClassSymbol c = cls; c != null; c = superclassOf(c)) {
            if (c == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** The direct superclass of {@code cls}, or null at the top of the chain. */
    private static Symbol.ClassSymbol superclassOf(Symbol.ClassSymbol cls) {
        return (cls.getSuperclass() instanceof com.sun.tools.javac.code.Type.ClassType sup
                && sup.tsym instanceof Symbol.ClassSymbol parent) ? parent : null;
    }

    /**
     * Least common ancestor of two classes along their superclass chains.
     * Interfaces are irrelevant: thrown types are classes and Java classes have
     * single inheritance. Always terminates at worst at {@code java.lang.Object}.
     */
    private static Symbol.ClassSymbol leastCommonAncestor(Symbol.ClassSymbol a,
                                                          Symbol.ClassSymbol b) {
        var bChain = new LinkedHashSet<Symbol.ClassSymbol>();
        for (Symbol.ClassSymbol c = b; c != null; c = superclassOf(c)) {
            bChain.add(c);
        }
        for (Symbol.ClassSymbol c = a; c != null; c = superclassOf(c)) {
            if (bChain.contains(c)) {
                return c;
            }
        }
        // Unreachable for thrown types (all chains meet at Throwable/Object),
        // but fail loud rather than emit a type the join cannot name.
        throw new JavaViolationException("thrown types share no common ancestor");
    }

    /**
     * Declare {@code cls} and its superclass chain up to and including
     * {@code java.lang.Throwable} (or the top of the chain if {@code Throwable}
     * is not an ancestor). Used by catch translation, where type dispatch needs
     * cross-hierarchy relations that the declared-join-scoped registration
     * cannot provide.
     */
    /**
     * True for a cast that NARROWS to a composite this compilation declares -- the
     * case Laurel's {@code as} exists for. Excludes: a parameterised target (the
     * {@code as} type slot is a bare identifier), a library type (may be modelled as
     * a Laurel value rather than a composite), and a widening or same-type cast,
     * which needs no runtime check and is the identity.
     */
    private boolean isOwnDowncast(JCTree.JCTypeCast cast,
                                  com.sun.tools.javac.code.Type.ClassType target,
                                  Symbol.ClassSymbol cls) {
        if (!target.getTypeArguments().isEmpty()
                || cls.sourcefile == null
                || !ownSourceFiles.contains(cls.sourcefile)
                || cast.expr.type == null
                || cast.expr.type.isPrimitive()) {
            return false;
        }
        var from = javacTypes.erasure(cast.expr.type);
        var to = javacTypes.erasure(target);
        // A widening (or identity) cast is handled without a check; only the
        // strictly narrowing direction needs `as`.
        return !javacTypes.isSubtype(from, to);
    }

    private void registerHierarchyUpToThrowable(Symbol.ClassSymbol cls) {
        Symbol.ClassSymbol stop = cls;
        for (Symbol.ClassSymbol c = cls; c != null; c = superclassOf(c)) {
            stop = c;
            if (c.getQualifiedName().contentEquals("java.lang.Throwable")) {
                break;
            }
        }
        registerHierarchyUpTo(cls, stop);
        referencedCompositeTypes.putIfAbsent(compositeSortName(stop), stop);
    }

    /**
     * Record that composite {@code child} extends {@code parent}, declaring
     * both sorts. Membership in {@link #referencedCompositeTypes} and the edge
     * in {@link #compositeExtends} always move together through this method,
     * so a linked child cannot silently miss its declaration (or dangle).
     *
     * <p>The {@code compositeExtends} edge is no longer what EMITS the parent.
     * {@code emitComposite} derives each sort's parents from its javac symbol
     * ({@code getSuperclass()} + {@code getInterfaces()}), which covers the whole
     * supertype closure rather than one recorded edge, and subsumes what this map fed
     * before -- verified: removing the write below leaves the suite green, and all six
     * exception fixtures (five of which exercise exception subtype hierarchies) still
     * pass. The map is kept for the conflict check that follows: two distinct classes
     * whose sort names collide (a nested {@code Outer.Inner} versus a top-level class so
     * named) must be refused loudly rather than silently keeping whichever arrived
     * first.
     *
     * <p>Sort names are the key, and {@link #compositeSortName} folds the
     * nested-class {@code $} into {@code .}, so a nested {@code Outer$Inner}
     * and a top-level class named {@code Outer.Inner} would share a key; a
     * conflicting parent edge for one key is refused loudly rather than
     * silently keeping whichever arrived first.
     */
    private void linkExtends(Symbol.ClassSymbol child, Symbol.ClassSymbol parent) {
        String childSort = compositeSortName(child);
        String parentSort = compositeSortName(parent);
        referencedCompositeTypes.putIfAbsent(childSort, child);
        referencedCompositeTypes.putIfAbsent(parentSort, parent);
        String existing = compositeExtends.putIfAbsent(childSort, parentSort);
        if (existing != null && !existing.equals(parentSort)) {
            throw new JavaViolationException(
                    "conflicting superclass links for composite '" + childSort
                            + "': '" + existing + "' vs '" + parentSort + "'");
        }
    }

    /**
     * Declare {@code cls} and every class on its superclass chain up to (but
     * not including) {@code stopAt} as composites, each with its {@code extends}
     * link recorded via {@link #linkExtends}. {@code stopAt} itself is
     * declared by the caller, without a link -- nothing above the stop is
     * dragged into the program.
     */
    private void registerHierarchyUpTo(Symbol.ClassSymbol cls, Symbol.ClassSymbol stopAt) {
        if (!isSubclassOf(cls, stopAt)) {
            // Internal precondition, not a user error: callers derive stopAt
            // from cls's own superclass chain. Violating it would make the
            // `c != stopAt` loop walk to the top, silently dragging every
            // class above the intended stop into the program.
            throw new JavaViolationException(
                    "internal: hierarchy stop '" + stopAt.getQualifiedName()
                            + "' is not an ancestor of '" + cls.getQualifiedName() + "'");
        }
        for (Symbol.ClassSymbol c = cls; c != null && c != stopAt; c = superclassOf(c)) {
            Symbol.ClassSymbol parent = superclassOf(c);
            if (parent == null) {
                break;
            }
            linkExtends(c, parent);
        }
    }

    /**
     * Ensure the composite owning {@code fieldSym} is declared with a
     * declaration for that specific field, so Strata's heap parameterization
     * can resolve {@code owner.field} reads/writes. Called from the
     * field-access (read) and field-assign (write) arms of
     * {@link StaticMethodCollector#convertExpression}.
     *
     * <p>Only the accessed field is emitted, not every field on the class:
     * for library-contract classes ({@code @Contract(SomeLibraryClass.class)}),
     * {@link org.strata.jverify.verifier.compiler.simplifications.ExternalContractCompiler}
     * re-parents the contract's ghost fields onto the library class's symbol,
     * so walking every declared field on {@code owner} would drag in the
     * library class's real (often internally-represented, e.g. {@code int[]})
     * fields that JVerify cannot translate. Emitting only the accessed field
     * side-steps that entirely.
     *
     * <p>Fields are kept in insertion order (via {@link LinkedHashMap}) for
     * deterministic output; subsequent references to the same field on the
     * same owner are idempotent.
     */
    private void ensureFieldOnComposite(Symbol.VarSymbol fieldSym) {
        if (!(fieldSym.owner instanceof Symbol.ClassSymbol owner)) {
            // Field owned by something other than a class (e.g. a lambda's synthetic
            // capture) -- Strata cannot represent this as a composite field. Skip;
            // the emit-time resolver will surface a "field not found" if the ref
            // actually reaches Laurel.
            return;
        }
        String sortName = compositeSortName(owner);
        referencedCompositeTypes.putIfAbsent(sortName, owner);
        LinkedHashMap<String, Field> fields = compositeFields.computeIfAbsent(
                sortName, k -> new LinkedHashMap<>());
        String name = fieldSym.name.toString();
        if (fields.containsKey(name)) return;
        HighType fieldType = translateType(fieldSym.type);
        // `isMutable` replaces the old immutableField/mutableField pair: a
        // `final` Java field is not mutable.
        boolean isMutable = (fieldSym.flags() & Flags.FINAL) == 0;
        fields.put(name, new Field(ident(name), isMutable, node(fieldType), /*initializer=*/Optional.empty()));
    }

    /**
     * True iff {@code expr} is a call to a JVerify library method whose simple
     * name is {@code name} (e.g. `everything`, `isAbstract`). Used to
     * recognise JVerify spec-only stubs that appear as arguments to contract
     * calls such as {@code modifies(everything())}.
     */
    private static boolean isJVerifyCallNamed(JCTree.JCExpression expr, String name) {
        if (!(expr instanceof JCTree.JCMethodInvocation inv)) return false;
        var jverifyMethod = JVerifyUtils.getJVerifyMethod(inv);
        return jverifyMethod != null
                && jverifyMethod.getQualifiedName().toString().equals(name);
    }

    private String qualifiedMethodName(Symbol.MethodSymbol sym) {
        // Package-qualified IMMEDIATE-enclosing class name ('$' -> '.'), then
        // MANGLE_SEPARATOR, then the method, then its overload signature. The
        // immediate (not outermost) class keeps nested-class methods distinct:
        // `Outer.Inner.foo` mangles to `Outer.Inner?foo` instead of colliding with
        // `Outer?foo`.
        return enclosingClassOrRefuse(sym).getQualifiedName().toString().replace('$', '.')
                + MANGLE_SEPARATOR + sym.name + overloadSuffix(sym);
    }

    /**
     * The suffix that distinguishes an overloaded method from its same-named
     * siblings, appended to the emitted Laurel procedure name so a by-name call
     * ({@code Class?m} for a static, {@code obj#m} for an instance) still selects
     * the right one. Empty for a no-arg method; otherwise MANGLE_SEPARATOR then
     * each parameter's ERASED type.
     *
     * <p>Erased on purpose: an override has the same erased signature as the base
     * (JLS override rules), so it mangles to the SAME name -- dispatch families
     * and contract binding still line up. Only overLOADs (different signatures)
     * get different names. A no-arg method keeps its bare name, so the common case
     * and every previously-emitted single-arity name are unchanged. Computed from
     * the symbol alone, so a declaration and every call site agree.
     */
    /** True unless the string contains an unpaired UTF-16 surrogate (no UTF-8 encoding). */
    private static boolean isEncodableUtf16(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    private String overloadSuffix(Symbol.MethodSymbol sym) {
        // Only overloaded names get a suffix (matching the original scheme): a
        // name that denotes a single signature keeps its bare emitted name, so the
        // common case and every previously-emitted name are unchanged. "Overloaded"
        // is measured over the TYPE CLOSURE OF THE ROOT DECLARATION of `sym`'s
        // override chain (see {@link #overloadScope}), not over `sym`'s own
        // declaring class, so an override and its base always agree on the name.
        // An overload split across an inheritance chain (base m(int), sub m(String))
        // is still caught, because the sub-declared sibling is its OWN root and its
        // closure sees both signatures.
        if (!isOverloaded(sym)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (com.sun.tools.javac.code.Type pt : javacTypes.erasure(sym.type).getParameterTypes()) {
            sb.append(MANGLE_SEPARATOR).append(pt.toString());
        }
        return sb.toString();
    }

    /** The erased parameter-type list of {@code sym}, the key an override shares with its base. */
    private String erasedSignature(Symbol.MethodSymbol sym) {
        return javacTypes.erasure(sym.type).getParameterTypes().toString();
    }

    /**
     * The class whose type closure decides {@code sym}'s overload suffix: the
     * ROOT DECLARATION of {@code sym}'s override chain -- the topmost class in
     * {@code sym}'s ancestry that declares a method {@code sym} overrides -- or
     * {@code sym}'s own declaring class when {@code sym} overrides nothing.
     *
     * <p>Why not the declaring class: "overloaded" measured over the DECLARING
     * class's closure is not stable along an override chain, so a base and its
     * override could emit DIFFERENT names. With {@code Base.m(int)} and
     * {@code Sub extends Base} declaring both {@code m(int)} (an override) and
     * {@code m(String)} (a new overload), closure(Base) sees one signature so
     * {@code Base.m(int)} stayed bare while closure(Sub) sees two so
     * {@code Sub.m(int)} became {@code m?int}. Distinct names mean Strata never
     * pairs them, so {@code CheckOverrideRefinement} never generates the Liskov
     * obligation that makes reasoning against the static type sound -- the program
     * verified while its override broke the base's postcondition. Keying on the
     * root makes base and override share a scope, hence a name, by construction;
     * the subclass-added overload is its own root and still gets a distinct name.
     *
     * <p>{@code static} and {@code private} methods never override, so they are
     * their own root. A member of a supertype only counts as overridden when it is
     * itself inheritable (not static, private or synthetic) and has {@code sym}'s
     * erased signature -- the same key the suffix is built from.
     *
     * <p>PLANNED REMOVAL. This renaming is a stopgap and is meant to be deleted, not
     * extended: the intended fix is overload support for composite INSTANCE methods in
     * Laurel, after which a Java overload maps straight onto a Laurel overload and
     * nothing here needs a suffix. Laurel already resolves overloaded STATIC procedures
     * by signature -- measured: two procedures both emitted as {@code C?static?dup},
     * with contradictory postconditions ({@code r == 1} and {@code r == 2}), each
     * verified at its own call site. Only composite instance methods still reject a
     * repeated name, with {@code Duplicate definition 'C.Sub$m' is already defined in
     * this scope}, which is the sole reason any of this exists.
     *
     * <p>KNOWN GAP, deliberately left for that work. A name with two INDEPENDENT roots
     * cannot be handled by any symbol-local scheme, this one included: with
     * {@code I1.m(int)} and {@code I2.m(String)} as separate interface defaults and
     * {@code Sub implements I1, I2} overriding both, each override's root is its own
     * interface and each closure sees one signature, so both emit bare {@code m} and
     * Strata reports a duplicate definition. The name has to be computable from the
     * symbol alone and only supertype information is reachable, so two incomparable
     * roots cannot both be agreed with. This is not a soundness regression -- with the
     * older declaring-class scoping the same shape was mis-named and its Liskov
     * obligation was never generated either, so it failed too, just more quietly. Fixing
     * it by naming (a whole-program family-to-name table) is deliberately NOT done,
     * because the Laurel change removes the need for names entirely.
     */
    private Symbol.ClassSymbol overloadScope(Symbol.MethodSymbol sym) {
        Symbol.ClassSymbol owner = enclosingClassOrRefuse(sym);
        if ((sym.flags() & (Flags.STATIC | Flags.PRIVATE)) != 0) {
            return owner;
        }
        String signature = erasedSignature(sym);
        Symbol.ClassSymbol scope = owner;
        for (com.sun.tools.javac.code.Type supertype : javacTypes.closure(owner.type)) {
            if (!(supertype.tsym instanceof Symbol.ClassSymbol cls) || cls == scope) {
                continue;
            }
            boolean declaresOverridden = false;
            for (Symbol member : cls.members().getSymbolsByName(sym.name)) {
                if (member instanceof Symbol.MethodSymbol m
                        && (m.flags() & (Flags.SYNTHETIC | Flags.STATIC | Flags.PRIVATE)) == 0
                        && erasedSignature(m).equals(signature)) {
                    declaresOverridden = true;
                    break;
                }
            }
            // Keep the HIGHEST declarer: only move up when the candidate is a
            // supertype of the one held, so the result does not depend on the
            // order `closure` happens to return.
            if (declaresOverridden
                    && javacTypes.isSubtype(javacTypes.erasure(scope.type), javacTypes.erasure(cls.type))) {
                scope = cls;
            }
        }
        return scope;
    }

    /**
     * Whether {@code sym}'s name denotes more than one distinct ERASED signature
     * in the closure of its {@linkplain #overloadScope override-chain root} -- i.e.
     * it is overLOADed (as opposed to a lone method or a pure overRIDE, which share
     * one signature). Synthetic members are excluded, mirroring the old overload
     * gates.
     */
    private boolean isOverloaded(Symbol.MethodSymbol sym) {
        Set<String> signatures = new java.util.HashSet<>();
        try {
            Symbol.ClassSymbol scope = overloadScope(sym);
            // Only mangle methods rooted in the program under verification. A
            // library / JDK method (java.util.List.of, ...) is modeled by a builtin
            // @Contract that declares one canonical entry per name, so a call must
            // emit the bare name to resolve against it -- mangling it by the JDK's
            // real overload set (List.of has ~12) would name a procedure the contract
            // never defined. Gating on the ROOT rather than the declaring class also
            // keeps a user override of a library method (`equals(Object)`) on the
            // library's canonical name, which is the name its contract defines.
            // Decl and call alike are gated the same way, so the emitted names agree.
            if (scope.sourcefile == null || !ownSourceFiles.contains(scope.sourcefile)) {
                return false;
            }
            for (com.sun.tools.javac.code.Type supertype : javacTypes.closure(scope.type)) {
                if (!(supertype.tsym instanceof Symbol.ClassSymbol cls)) {
                    continue;
                }
                // Opted-out methods COUNT. They used to be excluded, which was only
                // sound while a skipped method was dropped outright: a skipped PURE
                // method now keeps an opaque declaration (see `declarationOnly`), so it
                // is emitted under this same name. Excluding it made
                // `@Pure int dup(int)` and `@Verify(false) @Pure int dup(boolean)` each
                // see one signature, so both emitted a bare `dup` -- a duplicate
                // procedure, and an ambiguous call.
                for (Symbol member : cls.members().getSymbolsByName(sym.name)) {
                    if (member instanceof Symbol.MethodSymbol m
                            && (m.flags() & Flags.SYNTHETIC) == 0) {
                        signatures.add(erasedSignature(m));
                    }
                }
            }
        } catch (Symbol.CompletionFailure e) {
            // A supertype that will not load: treat as overloaded so the name is
            // disambiguated defensively rather than risking a silent collision.
            return true;
        }
        return signatures.size() > 1;
    }

    private void emitCalledContractStatics(LoweredResult loweredResult, List<LaurelFile> result,
                                           Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps) {
        var emitted = new HashSet<Symbol.MethodSymbol>();
        var procsByUnit = new LinkedHashMap<JCTree.JCCompilationUnit, List<Procedure>>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var unit : loweredResult.parsed()) {
                if (!lowerer.isContractSource(unit)) {
                    continue;
                }
                currentCompilationUnit = unit;
                reporter.compilationUnit = unit;
                var collector = new CalledContractStaticCollector(emitted);
                unit.accept(collector);
                if (collector.visited) {
                    changed = true;
                    var procs = procsByUnit.computeIfAbsent(unit, k -> new ArrayList<>());
                    for (var entry : castHelpers.entrySet()) {
                        if (emittedCastHelpers.add(entry.getKey())) {
                            procs.add(entry.getValue());
                        }
                    }
                    procs.addAll(collector.procedures);
                }
            }
        }
        for (var entry : procsByUnit.entrySet()) {
            var unit = entry.getKey();
            result.add(new LaurelFile(unit.sourcefile.toUri(),
                    new Program(entry.getValue(), List.of(), List.of(), List.of())));
            lineMaps.put(unit.sourcefile.toUri(), unit.getLineMap());
        }
    }

    private class CalledContractStaticCollector extends StaticMethodCollector {
        private final Set<Symbol.MethodSymbol> emitted;
        boolean visited = false;

        CalledContractStaticCollector(Set<Symbol.MethodSymbol> emitted) {
            this.emitted = emitted;
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            if ((method.mods.flags & Flags.STATIC) != 0
                    && calledStaticMethods.contains(method.sym)
                    && emitted.add(method.sym)) {
                visited = true;
                try {
                    ((StaticMethodCollector) this).translateMethod(method, true);
                } catch (JavaViolationException e) {
                    return;
                }
            }
        }
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();
        /// Class enclosing the method being translated, i.e. the static type of
        /// `this`. An implicit-`this` call emits `self?#m(...)` with `self?` typed
        /// as THIS class, so Strata resolves against this class's ancestry, not the
        /// callee declarer's.
        private Symbol.ClassSymbol currentEnclosingClass = null;
        private final RefusalGates gates =
                new RefusalGates(javacTypes, optedOutMethods);
        /** True while converting a requires/ensures/invariant expression. */
        private boolean inContractContext = false;
        private int labelCounter = 0;

        /** isLoop distinguishes loop entries from labeled-block entries: an
         *  unlabeled break may only target a loop (JLS 14.15), never a labeled
         *  block, and a switch never reaches here (SwitchDesugarer rewrites
         *  switch-targeted breaks into labeled breaks). */
        private record LabelEntry(String javaLabel, String breakLabel, String continueLabel, boolean isLoop) {}
        private final Deque<LabelEntry> labelStack = new ArrayDeque<>();
        private String pendingLabel = null;

        private boolean containsBreakOrContinue(JCTree.JCStatement stmt) {
            boolean[] found = {false};
            stmt.accept(new TreeScanner() {
                @Override public void visitBreak(JCTree.JCBreak tree) { found[0] = true; }
                @Override public void visitContinue(JCTree.JCContinue tree) { found[0] = true; }
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) {}
                @Override public void visitForLoop(JCTree.JCForLoop tree) {}
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) {}
            });
            return found[0];
        }

        /**
         * Check if a method body has any effect that Strata's frame check
         * classifies as a heap write:
         * <ul>
         *   <li>an instance-field assignment ({@code JCAssign} whose LHS is
         *       a non-static {@code JCFieldAccess}),</li>
         *   <li>a pre/post-{@code ++}/{@code --} whose target is a
         *       non-static field ({@code convertUnary} emits these as
         *       Laurel {@code preIncr}/{@code postIncr}/etc. on the
         *       field-access, which is a real heap write),</li>
         *   <li>a compound assignment ({@code JCAssignOp}) whose LHS is a
         *       non-static field, defensively (compound assign on fields
         *       is currently rejected up front elsewhere, but if that
         *       changes we don't want a silent regression here),</li>
         *   <li>a call to a non-JVerify, non-{@code @Pure} method (which
         *       conservatively may itself write the heap; we do not run
         *       our own transitive effect analysis and cannot inspect
         *       callee bodies here).</li>
         * </ul>
         *
         * <p>{@code @Pure} user methods are exempt from the call-triggered
         * fallback: pure methods have no heap effect by definition, and
         * treating them as heap-writing would force every caller into an
         * OpaqueSpec, defeating call-site inlining and call-determinism
         * for the {@code @Pure}-composition pattern this feature is meant
         * to enable.
         *
         * <p>Spec-method subtrees ({@code precondition(...)},
         * {@code postcondition(...)}, {@code invariant(...)},
         * {@code reads(...)}, {@code modifies(...)}, {@code decreases(...)})
         * describe the method's specification, not its runtime effect, so
         * traversal stops at their argument lambda. Otherwise an impure
         * call *inside a postcondition lambda* would incorrectly mark the
         * enclosing method as heap-writing.
         *
         * <p>When the user did not supply {@code modifies(...)} but this
         * method returns {@code true}, a wildcard {@code modifies *} is
         * synthesized so Strata accepts both the direct write and any
         * transitively propagated heap effect from an impure callee.
         * Sound but coarse: callers must assume every field may change.
         * A follow-up will narrow to per-field clauses once we track
         * write targets precisely enough.
         */
        private boolean bodyPotentiallyWritesHeap(JCTree.JCBlock body) {
            boolean[] found = {false};
            body.accept(new TreeScanner() {
                @Override public void visitAssign(JCTree.JCAssign tree) {
                    if (writesInstanceField(tree.lhs)) found[0] = true;
                    super.visitAssign(tree);
                }
                @Override public void visitUnary(JCTree.JCUnary tree) {
                    var tag = tree.getTag();
                    if ((tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC
                            || tag == JCTree.Tag.POSTDEC || tag == JCTree.Tag.PREDEC)
                            && writesInstanceField(tree.arg)) {
                        found[0] = true;
                    }
                    super.visitUnary(tree);
                }
                // Reached only for a target the translator accepts; `f += 1` on an
                // instance field is refused earlier as an unsupported JCAssignOp, so no
                // compound assignment to a field reaches the frame decision. This arm
                // exists for symmetry with visitAssign.
                @Override public void visitAssignop(JCTree.JCAssignOp tree) {
                    if (writesInstanceField(tree.lhs)) found[0] = true;
                    super.visitAssignop(tree);
                }
                @Override public void visitApply(JCTree.JCMethodInvocation tree) {
                    // Spec-method calls (precondition / postcondition /
                    // invariant / reads / modifies / decreases) describe the
                    // method's specification, not its runtime effect. Skip
                    // their subtrees entirely so an impure call inside a
                    // postcondition lambda doesn't mark the enclosing method
                    // as heap-writing.
                    if (isSpecMethodCall(tree)) return;
                    // Calls to the JVerify library (check/assume/etc.) never
                    // write the heap. @Pure user methods also have no heap
                    // effect; treating them as heap-writing would force their
                    // callers into an OpaqueSpec and defeat call-site
                    // inlining for the @Pure-composition pattern.
                    if (JVerifyUtils.getJVerifyMethod(tree) == null
                            && !isPureCall(tree)) {
                        found[0] = true;
                    }
                    super.visitApply(tree);
                }

                /**
                 * Whether this assignment target names heap state.
                 *
                 * <p>Both spellings count. A bare `f = ...` in an instance method is
                 * an implicit `this.f = ...` and reaches javac as a `JCIdent`, not a
                 * `JCFieldAccess`; matching only the qualified form left the
                 * synthesized `modifies` wildcard off such a method, whose frame check
                 * then failed on a write the frame should have covered.
                 * `isHeapInstanceField` already encodes the non-static, non-constant
                 * field test used elsewhere for exactly this decision.
                 */
                private boolean writesInstanceField(JCTree.JCExpression lhs) {
                    return switch (lhs) {
                        case JCTree.JCFieldAccess fa -> isHeapInstanceField(fa.sym);
                        case JCTree.JCIdent id -> isHeapInstanceField(id.sym);
                        default -> false;
                    };
                }

                private boolean isPureCall(JCTree.JCMethodInvocation tree) {
                    return TreeInfo.symbol(tree.getMethodSelect())
                            instanceof Symbol.MethodSymbol ms
                            && jverifyUtils.isPure(ms);
                }

                private boolean isSpecMethodCall(JCTree.JCMethodInvocation tree) {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(tree);
                    if (jverifyMethod == null) return false;
                    return switch (jverifyMethod.getQualifiedName().toString()) {
                        case "precondition", "postcondition", "invariant",
                                "reads", "modifies", "decreases" -> true;
                        default -> false;
                    };
                }
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
                @Override public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) { found[0] = true; }
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) { found[0] = true; }
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) { found[0] = true; }
            });
            return found[0];
        }

        private String freshLabel(String prefix) {
            return prefix + "_" + (labelCounter++);
        }

        private StmtExpr withLoopLabels(JCTree.JCStatement loopBody, java.util.function.BiFunction<String, String, StmtExpr> body) {
            boolean needsLabels = pendingLabel != null || containsBreakOrContinue(loopBody);
            String breakLbl = needsLabels ? freshLabel("loop_break") : null;
            String continueLbl = needsLabels ? freshLabel("loop_continue") : null;
            String javaLabel = pendingLabel;
            pendingLabel = null;
            labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl, true));
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
            } else {
                // JLS 14.15: an unlabeled break targets the innermost enclosing
                // loop (or switch, but SwitchDesugarer has already rewritten
                // switch-targeted breaks into labeled breaks), so walk past
                // labeled-block entries instead of taking the stack top.
                for (var entry : labelStack) {
                    if (entry.isLoop) {
                        // A loop entry with a null breakLabel means
                        // containsBreakOrContinue missed this break; resolving
                        // past it to an outer loop would be the original bug
                        // again, so refuse instead.
                        if (entry.breakLabel == null) break;
                        return entry.breakLabel;
                    }
                }
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
                    if (entry.isLoop) {
                        // Same backstop as resolveBreakLabel: resolving past a
                        // label-less loop entry to an outer loop would be the
                        // D566037730 bug for continue, so refuse instead.
                        if (entry.continueLabel == null) break;
                        return entry.continueLabel;
                    }
                }
            }
            throw new JavaViolationException("continue target not found");
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            boolean isStatic = (method.mods.flags & Flags.STATIC) != 0;
            // Deferred, and already skipped by the previous STATIC-only gate, so
            // skipping them keeps previously-green classes green: constructors
            // (emitting one would embed '<init>' in the name and expose the
            // synthesized super() chain), synthetic members, and methods on an
            // anonymous/local class (no qualified name on the enclosing class, so
            // its sort is only reachable by flatname).
            boolean deferred = JVerifyUtils.isConstructor(method.sym)
                    || (method.mods.flags & Flags.SYNTHETIC) != 0
                    || method.sym.enclClass() == null
                    || method.sym.enclClass().getQualifiedName().isEmpty();
            // A deferred member emits no Laurel but still holds a Verified entry in
            // the interval tree, so a user constructor or anon-class method with a
            // real contract would be reported Verified though Strata never checked
            // it, and its unchecked obligations can surface against whatever the
            // contract mentions. Demote it. Exception: a GENERATED implicit
            // constructor has no user body, so its vacuous Verified is sound (the
            // documented "+1 per class" convention). markSkipped no-ops without an
            // entry, so this cannot inflate the Skipped count.
            if (deferred && !isStatic && (method.mods.flags & Flags.GENERATEDCONSTR) == 0) {
                annotationCompiler.markSkipped(currentCompilationUnit, method);
            }
            if (isStatic || (!deferred && !jverifyUtils.isInterpretEntry(method.sym))) {
                try {
                    translateMethod(method, isStatic);
                } catch (JavaViolationException e) {
                    // A refusal can fire between a JCLabeledStatement setting
                    // pendingLabel and the loop consuming it; without this
                    // reset the stale label would attach to the next method's
                    // first loop.
                    pendingLabel = null;
                    // Demote so an un-emitted method is not counted Verified. This
                    // demotion is what makes the asymmetry below safe: the fallback
                    // state is Skipped (an honest "not checked"), not Verified.
                    // Remove it and suppressing the diagnostic hides an unchecked
                    // method behind a green run -- the two are coupled.
                    annotationCompiler.markSkipped(currentCompilationUnit, method);
                    if (isStatic) {
                        reporter.reportError(method, "translatorError", e.getMessage());
                    } else {
                        // Instance support is partial, so these bodies often hit a
                        // not-yet-supported construct; an error per method would be
                        // noise and fail builds over a known gap. But silence costs
                        // the user the reason and the location: the only trace is the
                        // aggregate "Skipped methods: N", which maps back to neither.
                        // Warn instead, deduplicated by (method, reason) so a
                        // construct hit repeatedly reports once.
                        // ... unless a hard error already names a construct inside
                        // this method, in which case the warning only repeats it
                        // less precisely (see hasReportedErrorInside).
                        if (firstReportOf(method.sym, e.getMessage())
                                && !hasReportedErrorInside(method)) {
                            reporter.reportDiagnostic(method,
                                    JCDiagnostic.DiagnosticType.WARNING,
                                    "instanceMethodNotVerified",
                                    method.name.toString(), e.getMessage());
                        }
                    }
                }
            } else if (jverifyUtils.isInterpretEntry(method.sym)) {
                // @InterpretEntry marks a concrete-interpretation entry point that
                // the interpreter runs as a Laurel/Core entry procedure. Only static
                // methods are translated (a non-static method has no runnable entry
                // procedure and no receiver to execute against), so reject the marker
                // here with a clear diagnostic instead of silently ignoring it.
                reporter.reportError(method, "translatorError",
                        "@InterpretEntry method '" + method.name + "' must be static");
                annotationCompiler.markSkipped(currentCompilationUnit, method);
            }
            super.visitMethodDef(method);
        }

        private void translateMethod(JCTree.JCMethodDecl method, boolean isStatic) {
            // No overload refusal: an overloaded method's emitted name carries its
            // erased parameter signature (see overloadSuffix), so same-named
            // siblings get distinct Laurel names and by-name resolution is
            // unambiguous.
            currentEnclosingClass = enclosingClassOrRefuse(method.sym);
            currentMethodSymbol = method.sym;
            // An instance procedure lives INSIDE its composite under the bare method
            // name; Strata's LiftInstanceProcedures pass lifts it to a top-level
            // `<Composite>$<method>`. A static keeps the mangled name.
            String methodName = isStatic
                    ? qualifiedMethodName(method.sym)
                    : method.sym.name.toString() + overloadSuffix(method.sym);

            List<Parameter> inputs = new ArrayList<>();
            if (!isStatic) {
                // REQUIRED by Strata, not a choice here: LiftInstanceProcedures
                // rewrites `obj#method(args)` by PREPENDING the receiver to the
                // argument list, so the lifted procedure must declare it first for
                // the arities to line up.
                inputs.add(new Parameter(ident(LAUREL_SELF_BINDING),
                        node(translateType(enclosingClassOrRefuse(method.sym).type))));
            }
            for (var param : method.params) {
                refuseReservedName(param.name);
                inputs.add(new Parameter(ident(param.name.toString()),
                        translateTypeWithRange(param.type, param.mods, typeRangeOrNone(param.vartype))));
            }

            List<Parameter> outputs = new ArrayList<>();
            if (method.restype != null && method.restype.type != null
                    && method.restype.type.getTag() != TypeTag.VOID) {
                outputs.add(new Parameter(reservedIdent(LAUREL_RESULT_BINDING),
                        translateTypeWithRange(method.restype.type, null, typeRangeOrNone(method.restype))));
            }

            // Exceptional exit: the Java `throws` clause is the single source of
            // the declared exceptional type. The
            // binding name is synthesized; Java permits `$` in identifiers, but
            // the JLS reserves it for generated code, so a user identifier
            // spelled `java?thrown` is out-of-convention rather than impossible --
            // and Strata freshens its own carrier names independently. A method
            // with no `throws` clause keeps no exceptional exit, exactly as
            // before.
            Optional<AstNode<HighType>> throwsType = Optional.empty();
            Optional<Identifier> throwsBinding = Optional.empty();
            currentThrowsJoin = null;
            enclosingCatchTypes.clear();
            if (method.thrown != null && !method.thrown.isEmpty()) {
                DeclaredThrows declared = translateThrowsClause(method.thrown);
                currentThrowsJoin = declared.join();
                throwsType = Optional.of(declared.type());
                throwsBinding = Optional.of(ident(THROWN_BINDING));
            }

            List<Condition> preconditions = new ArrayList<>();
            List<Condition> postconditions = new ArrayList<>();
            List<ThrowsOnBlock> throwsOnBlocks = new ArrayList<>();
            // `Body.Opaque` carries modifies as a list of guarded groups; JVerify
            // emits the degenerate form every frontend uses -- one unguarded
            // group holding the user's targets (see `unguardedModifies`). Guards
            // are attached later, inside Strata, by `EliminateExceptions`.
            List<AstNode<StmtExpr>> modifies = new ArrayList<>();
            StmtExpr methodBody = null;

            if (method.body != null) {
                MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                // Mark the context so the JCNewClass arm refuses allocation inside a
                // contract expression (see that arm for why).
                inContractContext = true;
                try {
                for (var pre : contract.preconditions()) {
                    var preExpr = pre.get();
                    StmtExpr converted = (preExpr instanceof JCTree.JCLambda lambda)
                            ? convertLambdaBody(lambda, Map.of())
                            : convertExpression(preExpr);
                    preconditions.add(new Condition(node(converted, preExpr), Optional.empty(), new ConditionMode.Both()));
                }
                for (var post : contract.postconditions()) {
                    var postExpr = post.get();
                    if (postExpr instanceof JCTree.JCLambda lambda) {
                        Map<String, String> renames = lambda.params.size() == 1
                                ? Map.of(lambda.params.getFirst().name.toString(), LAUREL_RESULT_BINDING)
                                : Map.of();
                        postconditions.add(new Condition(node(convertLambdaBody(lambda, renames), lambda.body), Optional.empty(), new ConditionMode.Both()));
                    } else {
                        postconditions.add(new Condition(node(convertExpression(postExpr), postExpr), Optional.empty(), new ConditionMode.Both()));
                    }
                }

                for (var mod : contract.modifies()) {
                    modifies.add(convertModifiesEntry(mod.get()));
                }

                // Exceptional behavior cases:
                // each `throwsOn(...).postcondition(...).modifies(...)` chain becomes a
                // Laurel `ThrowsOnBlock`. The guard converts as a plain
                // boolean expression (Strata's resolution rejects heap-reading
                // guards with its own diagnostic); a case postcondition binds
                // the thrown value by renaming its lambda parameter to the
                // `throws (e: T)` binding; a case frame entry converts exactly
                // like a normal modifies entry.
                if (!contract.throwsOnCases().isEmpty() && currentThrowsJoin == null) {
                    throw new JavaViolationException(
                            "throwsOn requires the method to declare the thrown type"
                                    + " in its throws clause");
                }
                for (var c : contract.throwsOnCases()) {
                    var guardExpr = c.guard().get();
                    List<Condition> casePosts = new ArrayList<>();
                    for (var post : c.postconditions()) {
                        casePosts.add(convertCasePostcondition(post.get()));
                    }
                    List<AstNode<StmtExpr>> caseModifies = new ArrayList<>();
                    for (var mod : c.modifies()) {
                        caseModifies.add(convertModifiesEntry(mod.get()));
                    }
                    throwsOnBlocks.add(new ThrowsOnBlock(
                            node(convertExpression(guardExpr), guardExpr),
                            casePosts, caseModifies));
                }

                } finally {
                    inContractContext = false;
                }

                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(method.body);
                if (!implStatements.isEmpty()) {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var statement : implStatements) {
                        StmtExpr converted = convertStatement(statement);
                        if (converted != null) {
                            stmts.add(node(converted, sourceTreeFor(statement)));
                        }
                    }
                    methodBody = new StmtExpr.Block(stmts, Optional.empty());
                }

                // Fallback: if the user did not supply a `modifies(...)`
                // clause but the body potentially writes the heap (an
                // instance-field write, or a call to a non-JVerify method
                // that conservatively may write fields transitively), emit
                // a wildcard so Strata's frame check accepts the heap
                // effect. Sound but coarse -- callers must assume any
                // field may change. Users who need precision should write
                // an explicit `modifies(...)`.
                if (modifies.isEmpty() && bodyPotentiallyWritesHeap(method.body)) {
                    modifies.add(node(new StmtExpr.All(), method.body));
                }
            }

            boolean isPure = jverifyUtils.isPure(method.sym);
            if (isPure && method.body != null && methodBody != null
                    && bodyContainsLoop(method.body)) {
                if (!postconditions.isEmpty()) {
                    throw new JavaViolationException(
                        "@Pure function with a loop and a postcondition is not yet supported");
                }
                methodBody = null;
            }

            Body body;
            // A procedure can stay transparent only when it is pure and carries
            // neither postconditions nor modifies: `Body.Transparent` has
            // nowhere to put either, so anything with a frame or a spec must
            // become Opaque/Abstract.
            //
            // Nothing here carries `throwsOn` behaviour cases: in the record AST
            // those live on `Procedure.throwsOn` rather than on the body, so the
            // exceptional contract DSL (phase 4) supplies them at the
            // `new Procedure(...)` call below. The declared exceptional exit
            // itself (`throwsType`/`throwsBinding`) is translated above.
            //
            // A declared `throws` also forces Opaque: Strata rejects a transparent
            // procedure whose body throws, because a transparent body becomes a
            // function and throwing is not expressible as an expression. Gating on
            // the declaration rather than on whether the body actually throws is
            // deliberately conservative -- it costs transparency only for a `@Pure`
            // method that declares `throws` without ever throwing.
            boolean canStayTransparent =
                    isPure && postconditions.isEmpty() && modifies.isEmpty()
                            && throwsType.isEmpty();
            if (methodBody == null) {
                if (postconditions.isEmpty() && modifies.isEmpty()) {
                    // Opaque with no implementation = uninterpreted function
                    // (matches what the DDM path produces for a function with
                    // no body; Body.External is reserved for built-in primitives)
                    body = new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()));
                } else if (modifies.isEmpty()) {
                    body = new Body.Abstract(postconditions);
                } else {
                    // `Body.Abstract` cannot carry a frame, so a body-less
                    // procedure with modifies stays Opaque (no implementation).
                    body = new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(modifies));
                }
            } else if (canStayTransparent) {
                body = new Body.Transparent(node(methodBody));
            } else {
                body = new Body.Opaque(postconditions, Optional.of(node(methodBody)), unguardedModifies(modifies));
            }

            // Carry the producer's @InterpretEntry marker through to Laurel so
            // the interpreter can find the entry point via metadata. The
            // interpreter runs the marked method as a Core entry procedure, and
            // Strata rejects entry procedures that take inputs, so require a
            // no-argument method and block here with a clear diagnostic rather
            // than letting the whole compilation unit abort later at
            // Laurel-to-Core translation.
            //
            // In the record AST this is the `isInterpretEntry` flag rather than
            // a separate entry clause.
            boolean isInterpretEntry = jverifyUtils.isInterpretEntry(method.sym);
            if (isInterpretEntry && !inputs.isEmpty()) {
                throw new JavaViolationException(
                        "@InterpretEntry method '" + method.name + "' must take no arguments");
            }

            var proc = new Procedure(
                    ident(methodName, method),
                    method.typarams.stream().map(tp -> ident(tp.name.toString())).toList(),
                    inputs, outputs, preconditions, new CoroutineContracts.Regular(),
                    /*decreases=*/Optional.empty(), body, /*invokeOn=*/Optional.empty(),
                    isInterpretEntry, /*axioms=*/List.of(),
                    // Exceptional contract: the declared type and binding come from
                    // the Java `throws` clause, the behaviour cases from the
                    // `throwsOn(...).postcondition(...).modifies(...)` contract chains.
                    throwsType, throwsBinding, throwsOnBlocks,
                    /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
            procedureBySymbol.put(method.sym, proc);
            if (isStatic) {
                procedures.add(proc);
            } else {
                // Emitted inside the composite (see emitComposite), not at top level.
                Symbol.ClassSymbol owner = enclosingClassOrRefuse(method.sym);
                String sort = compositeSortName(owner);
                referencedCompositeTypes.putIfAbsent(sort, owner);
                instanceProcsBySort.computeIfAbsent(sort, k -> new ArrayList<>()).add(proc);
            }
        }

        /**
         * Builds the Laurel `while` (plus the block scaffolding a `for`'s init/step
         * and any break/continue labels need) for a Java loop.
         *
         * <p>{@code loop} is the originating Java tree. Every node synthesized here
         * is stamped with its range: none corresponds to a distinct piece of Java
         * syntax, but a diagnostic can still land on one -- the implicit "invariant
         * holds on entry" check for a loop with no invariant lands on the `while`
         * itself -- and a range-less node makes it unattributable (see
         * {@link JavaToLaurelCompiler#ident(String, JCTree)}).
         */
        private StmtExpr emitLoop(JCTree loop, StmtExpr cond, List<AstNode<StmtExpr>> invariants, StmtExpr loopBody,
                                  StmtExpr step, List<StmtExpr> preamble,
                                  String breakLbl, String continueLbl) {
            if (breakLbl != null) {
                StmtExpr wrappedBody = new StmtExpr.Block(List.of(node(loopBody, loop)), Optional.of(continueLbl));
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = new StmtExpr.Block(List.of(node(wrappedBody, loop), node(step, loop)), Optional.empty());
                } else {
                    whileBody = wrappedBody;
                }
                StmtExpr whileNode = new StmtExpr.While(node(cond, loop), invariants, Optional.empty(), node(whileBody, loop), false);
                List<AstNode<StmtExpr>> outerStmts = new ArrayList<>();
                for (var p : preamble) outerStmts.add(node(p, loop));
                outerStmts.add(node(whileNode, loop));
                return new StmtExpr.Block(outerStmts, Optional.of(breakLbl));
            } else {
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = new StmtExpr.Block(List.of(node(loopBody, loop), node(step, loop)), Optional.empty());
                } else {
                    whileBody = loopBody;
                }
                StmtExpr whileNode = new StmtExpr.While(node(cond, loop), invariants, Optional.empty(), node(whileBody, loop), false);
                if (preamble.isEmpty()) {
                    return whileNode;
                }
                List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                for (var p : preamble) stmts.add(node(p, loop));
                stmts.add(node(whileNode, loop));
                return new StmtExpr.Block(stmts, Optional.empty());
            }
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return convertStatement(statement, Map.of());
        }

        private StmtExpr convertStatementOrEmpty(JCTree.JCStatement statement, Map<String, String> renames) {
            StmtExpr converted = convertStatement(statement, renames);
            return converted != null ? converted : new StmtExpr.Block(List.of(), Optional.empty());
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement, Map<String, String> renames) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        new StmtExpr.Assert(node(convertExpression(assertStmt.cond, renames)), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr, renames);
                case JCTree.JCBlock blk -> convertBlock(blk, renames);
                case JCTree.JCVariableDecl varDecl -> {
                    refuseReservedName(varDecl.name);
                    var typeNode = translateTypeWithRange(varDecl.type, varDecl.mods, typeRangeOrNone(varDecl.vartype));
                    // `Declare` takes a `ParameterOpt`: the annotation is optional in
                    // Laurel, but javac has always resolved the declared type by here
                    // (including for `var`), so it is present.
                    var param = new ParameterOpt(ident(varDecl.name.toString()), Optional.of(typeNode));
                    var decl = new Variable.Declare(param);
                    if (varDecl.init != null) {
                        yield new StmtExpr.Assign(
                            List.of(nodeVar(decl)),
                            node(convertExpression(varDecl.init, renames)));
                    } else {
                        yield new StmtExpr.Var(decl);
                    }
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond, renames);
                    StmtExpr thenBranch = convertStatementOrEmpty(ifStmt.thenpart, renames);
                    Optional<AstNode<StmtExpr>> elseNode = Optional.empty();
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart, renames);
                        if (elseStmt != null) {
                            elseNode = Optional.of(node(elseStmt, ifStmt.elsepart));
                        }
                    }
                    yield new StmtExpr.IfThenElse(node(cond, ifStmt.cond),
                            node(thenBranch, ifStmt.thenpart), elseNode);
                }
                case JCTree.JCReturn retStmt -> {
                    Optional<AstNode<StmtExpr>> value = retStmt.expr != null ? Optional.of(node(convertExpression(retStmt.expr, renames))) : Optional.empty();
                    yield new StmtExpr.Return(value);
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    yield withLoopLabels(whileStmt.body, (breakLbl, continueLbl) -> {
                        StmtExpr cond = convertExpression(whileStmt.cond, renames);
                        var parts = extractLoopParts(whileStmt.body, renames);
                        return emitLoop(whileStmt, cond, parts.invariants, parts.body, null, List.of(),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCForLoop forLoop -> {
                    if (forLoop.init.size() > 1 || forLoop.step.size() > 1)
                        throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                    yield withLoopLabels(forLoop.body, (breakLbl, continueLbl) -> {
                        StmtExpr init = forLoop.init.isEmpty() ? new StmtExpr.Block(List.of(), Optional.empty())
                                : convertStatement(forLoop.init.getFirst(), renames);
                        StmtExpr cond = forLoop.cond != null
                                ? convertExpression(forLoop.cond, renames)
                                : new StmtExpr.LiteralBool(true);
                        StmtExpr step = forLoop.step.isEmpty() ? new StmtExpr.Block(List.of(), Optional.empty())
                                : convertStatement(forLoop.step.getFirst(), renames);
                        var parts = extractLoopParts(forLoop.body, renames);
                        return emitLoop(forLoop, cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhile -> {
                    var breakLbl = freshLabel("loop_break");
                    var continueLbl = freshLabel("loop_continue");
                    var javaLabel = pendingLabel;
                    pendingLabel = null;
                    labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl, true));
                    try {
                        var cond = convertExpression(doWhile.cond, renames);
                        var parts = extractLoopParts(doWhile.body, renames);
                        var notCond = primitiveOp(new Operation.Not(), node(cond, doWhile.cond));
                        var exitIfDone = new StmtExpr.IfThenElse(node(notCond, doWhile.cond),
                                node(new StmtExpr.Exit(breakLbl), doWhile), Optional.empty());
                        var wrappedBody = new StmtExpr.Block(List.of(node(parts.body, doWhile)), Optional.of(continueLbl));
                        var whileBody = new StmtExpr.Block(List.of(node(wrappedBody, doWhile), node(exitIfDone, doWhile)), Optional.empty());
                        // Stamped for the reason given in `emitLoop`: synthesized, but
                        // still a landing site for the loop's implicit obligations.
                        var whileNode = new StmtExpr.While(node(new StmtExpr.LiteralBool(true), doWhile), parts.invariants, Optional.empty(), node(whileBody, doWhile), false);
                        yield new StmtExpr.Block(List.of(node(whileNode, doWhile)), Optional.of(breakLbl));
                    } finally {
                        labelStack.pop();
                    }
                }
                case JCTree.JCBreak breakStmt -> {
                    yield new StmtExpr.Exit(resolveBreakLabel(breakStmt));
                }
                case JCTree.JCContinue contStmt -> {
                    yield new StmtExpr.Exit(resolveContinueLabel(contStmt));
                }
                case JCTree.JCLabeledStatement labeledStmt -> {
                    pendingLabel = labeledStmt.label.toString();
                    if (labeledStmt.body instanceof JCTree.JCWhileLoop
                            || labeledStmt.body instanceof JCTree.JCForLoop
                            || labeledStmt.body instanceof JCTree.JCDoWhileLoop) {
                        yield convertStatement(labeledStmt.body, renames);
                    } else {
                        String brkLbl = freshLabel("label_break");
                        labelStack.push(new LabelEntry(pendingLabel, brkLbl, null, false));
                        pendingLabel = null;
                        try {
                            StmtExpr body = convertStatementOrEmpty(labeledStmt.body, renames);
                            yield new StmtExpr.Block(List.of(node(body)), Optional.of(brkLbl));
                        } finally {
                            labelStack.pop();
                        }
                    }
                }
                case JCTree.JCTry tryStmt -> {
                    // Defensive: javac's LOWER phase (already in the pipeline)
                    // desugars try-with-resources into try/finally before we
                    // run (verified -- see the TryWithResources fixture), so
                    // resources never survive to here today. If a javac change
                    // ever stops that, refuse rather than drop the close()
                    // semantics silently. Note the desugared form's synthesized
                    // `catch (Throwable t) ... throw t` still answers to the
                    // declared-throws rule, so try-with-resources in a
                    // non-throwing method is refused downstream with the
                    // declared-throws diagnostic (pinned by TryWithResources).
                    if (tryStmt.resources != null && !tryStmt.resources.isEmpty()) {
                        throw new JavaViolationException(
                                "try-with-resources is not supported; use try/finally");
                    }
                    // The arms' catch types license `throw`s inside the try
                    // BODY only (a throw in an arm or finalizer answers to the
                    // next try out, as in Java), so scope the push to the body.
                    List<Symbol.ClassSymbol> armTypes = new ArrayList<>();
                    for (var catcher : tryStmt.catchers) {
                        armTypes.addAll(caughtClassesOf(catcher));
                    }
                    StmtExpr tryBody;
                    enclosingCatchTypes.push(armTypes);
                    try {
                        tryBody = convertStatementOrEmpty(tryStmt.body, renames);
                    } finally {
                        enclosingCatchTypes.pop();
                    }
                    List<CatchClause> catches = new ArrayList<>();
                    for (var catcher : tryStmt.catchers) {
                        catches.add(convertCatcher(catcher, renames));
                    }
                    Optional<AstNode<StmtExpr>> finallyArm = tryStmt.finalizer != null
                            ? Optional.of(node(convertStatementOrEmpty(tryStmt.finalizer, renames),
                                    tryStmt.finalizer))
                            : Optional.empty();
                    yield new StmtExpr.Try(node(tryBody, tryStmt.body), catches, finallyArm);
                }
                case JCTree.JCThrow throwStmt -> {
                    if (!(throwStmt.expr.type instanceof com.sun.tools.javac.code.Type.ClassType ct)
                            || !(ct.tsym instanceof Symbol.ClassSymbol thrownCls)) {
                        throw new JavaViolationException(
                                "Unsupported thrown expression type: " + throwStmt.expr.type);
                    }
                    // A `throw` needs somewhere to land: an enclosing catch arm
                    // that covers its type, or the method's declared exceptional
                    // exit -- the declaration is
                    // where the thrown type, the caller's view, and the coming
                    // `throwsOn` contract DSL all hang. Java lets an unchecked
                    // throw go undeclared and uncaught; JVerify does not.
                    boolean caughtLocally = enclosingCatchTypes.stream().anyMatch(arms ->
                            arms.stream().anyMatch(c -> isSubclassOf(thrownCls, c)));
                    if (caughtLocally) {
                        // Link the whole chain: the catch guard's `is` test needs
                        // the thrown class related to the caught class.
                        registerHierarchyUpToThrowable(thrownCls);
                    } else if (currentThrowsJoin == null) {
                        throw new JavaViolationException(
                                "throw requires the enclosing method to declare the thrown type"
                                        + " in its throws clause");
                    } else if (!isSubclassOf(thrownCls, currentThrowsJoin)) {
                        // javac enforces declaration for checked exceptions only;
                        // an unchecked throw outside the declared hierarchy is
                        // legal Java, but under JVerify's declared-throws
                        // discipline it would escape the exceptional contract --
                        // reject it here rather than as a Strata-side type error.
                        throw new JavaViolationException(
                                "thrown type '" + thrownCls.getQualifiedName()
                                        + "' is not covered by the declared throws clause ('"
                                        + currentThrowsJoin.getQualifiedName() + "')");
                    } else {
                        // Link the whole chain, as the throws clause does: the
                        // thrown class must relate to the declared join for the
                        // Laurel `throw` to type-check, and to any supertype a
                        // caller may catch for that guard's `is` test to hold.
                        registerHierarchyUpToThrowable(thrownCls);
                    }
                    yield new StmtExpr.Throw(node(convertExpression(throwStmt.expr, renames), throwStmt.expr));
                }
                case JCTree.JCSkip ignored -> null;
                default -> throw new JavaViolationException("Unsupported statement: " + statement.getClass().getSimpleName());
            };
        }

        /**
         * A case's {@code .postcondition(...)} property. The lambda's one
         * parameter must be typed at the method's declared throws type -- the
         * property then converts with the parameter renamed to the
         * `throws (e: T)` binding, so it speaks about the thrown value
         * directly. Per-type claims narrow inside the property with
         * {@code instanceof} and a cast (both supported for exception types),
         * mirroring how Laurel's own per-type claims are written
         * (`e is T ==> (e as T)...`). Requiring the declared type keeps the
         * binding's typing exact; a subtype-typed parameter would silently
         * weaken nothing and dereference wrongly, so it is refused with the
         * narrowing idiom in the message.
         */
        /**
         * One {@code modifies}/case-{@code .modifies} frame entry.
         * {@code modifies(everything())} is the user's way of writing an
         * unconstrained frame: map it to Laurel's modifies wildcard
         * ({@code StmtExpr.All}) up front, because {@code everything()} is a
         * JVerify spec-only stub that {@code convertExpression} would refuse
         * ("Unsupported JVerify method: everything"). Shared by the
         * normal-exit and per-case frame loops so the bypass has one home.
         */
        private AstNode<StmtExpr> convertModifiesEntry(JCTree.JCExpression modExpr) {
            return isJVerifyCallNamed(modExpr, "everything")
                    ? node(new StmtExpr.All(), modExpr)
                    : node(convertExpression(modExpr), modExpr);
        }

        private Condition convertCasePostcondition(JCTree.JCExpression sigExpr) {
            // The unlambda phase may have wrapped the lambda in a functional-
            // interface cast (see LambdaToAnonymousClassCompiler.visitApply).
            while (sigExpr instanceof JCTree.JCTypeCast cast) {
                sigExpr = cast.expr;
            }
            if (!(sigExpr instanceof JCTree.JCLambda lambda) || lambda.params.size() != 1) {
                throw new JavaViolationException(
                        "the argument to a throwsOn case's postcondition must be a one-parameter lambda");
            }
            var param = lambda.params.getFirst();
            if (!(param.type instanceof com.sun.tools.javac.code.Type.ClassType ct)
                    || !(ct.tsym instanceof Symbol.ClassSymbol paramCls)
                    || paramCls != currentThrowsJoin) {
                throw new JavaViolationException(
                        "a throwsOn case's postcondition parameter must be typed at the method's declared throws type"
                                + (currentThrowsJoin != null
                                        ? " ('" + currentThrowsJoin.getQualifiedName() + "')"
                                        : "")
                                + "; narrow with instanceof and a cast inside the property");
            }
            Map<String, String> renames = Map.of(param.name.toString(), THROWN_BINDING);
            StmtExpr converted = convertLambdaBody(lambda, renames);
            return new Condition(node(converted, lambda.body), Optional.empty(),
                    new ConditionMode.Both());
        }

        /** The classes a catch arm's parameter covers (several for a multi-catch). */
        private List<Symbol.ClassSymbol> caughtClassesOf(JCTree.JCCatch catcher) {
            var param = catcher.param;
            List<Symbol.ClassSymbol> caught = new ArrayList<>();
            var alternatives = param.vartype instanceof JCTree.JCTypeUnion union
                    ? union.alternatives
                    : com.sun.tools.javac.util.List.of(param.vartype);
            for (var alt : alternatives) {
                if (!(alt.type instanceof com.sun.tools.javac.code.Type.ClassType ct)
                        || !(ct.tsym instanceof Symbol.ClassSymbol cls)) {
                    throw new JavaViolationException("Unsupported catch type: " + alt.type);
                }
                caught.add(cls);
            }
            return caught;
        }

        /**
         * A Java {@code catch (E e)} arm as a Laurel {@link CatchClause}: the
         * binding keeps its Java name, type dispatch becomes the guard
         * {@code e is E} (a multi-catch {@code catch (A | B e)} becomes the
         * disjunction of type tests, matching Java's own semantics of typing
         * the binding at the alternatives' LCA), and arms stay in source order
         * because Laurel tries clauses first-match-wins exactly as Java does.
         *
         * <p>The clause's {@code bindingType} is left {@code Unknown}: Strata's
         * resolution computes it as the LCA of the exception types thrown in
         * the {@code try} body, the same as for the Laurel textual frontend.
         *
         * <p>Each caught class is declared as a composite with its superclass
         * chain up to {@code java.lang.Throwable}, matching the registration
         * the throws-clause and throw sides perform. Both sides going all the
         * way up is what keeps every {@code is} test between exception types
         * meaningful ({@code e is E} over unrelated sorts would be vacuously
         * false, silently turning a live arm into dead code) -- including a
         * catch of a supertype of an exception that only arrives by
         * propagation from a callee. The JCL ancestors this drags in
         * ({@code Exception}, {@code Throwable}, ...) are empty,
         * uninterpreted sorts.
         */
        private CatchClause convertCatcher(JCTree.JCCatch catcher, Map<String, String> renames) {
            var param = catcher.param;
            String binding = param.name.toString();
            List<Symbol.ClassSymbol> caught = caughtClassesOf(catcher);
            StmtExpr predicate = null;
            for (var cls : caught) {
                registerHierarchyUpToThrowable(cls);
                String sort = compositeSortName(cls);
                StmtExpr test = new StmtExpr.IsType(
                        node(var_(binding), param),
                        node(new HighType.UserDefined(ident(sort)), typeRangeOrNone(param.vartype)));
                predicate = predicate == null
                        ? test
                        : primitiveOp(new Operation.Or(), node(predicate, param), node(test, param));
            }
            StmtExpr body = convertStatementOrEmpty(catcher.body, renames);
            return new CatchClause(ident(binding),
                    Optional.of(node(predicate, param)),
                    node(body, catcher.body),
                    node(new HighType.Unknown()));
        }

        private StmtExpr convertBlock(JCTree.JCBlock blk, Map<String, String> renames) {
            List<AstNode<StmtExpr>> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement, renames);
                if (converted != null) {
                    statements.add(node(converted, sourceTreeFor(statement)));
                }
            }
            return new StmtExpr.Block(statements, Optional.empty());
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

        private record LoopParts(List<AstNode<StmtExpr>> invariants, StmtExpr body) {}

        private LoopParts extractLoopParts(JCTree.JCStatement body, Map<String, String> renames) {
            if (body instanceof JCTree.JCBlock loopBlock) {
                if (!MethodOrLoopContractCompiler.hasContractStructure(loopBlock)) {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var s : loopBlock.getStatements()) {
                        StmtExpr converted = convertStatement(s, renames);
                        if (converted != null) stmts.add(node(converted, s));
                    }
                    return new LoopParts(List.of(), new StmtExpr.Block(stmts, Optional.empty()));
                }
                MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
                // (A `throwsOn` in a loop contract is rejected earlier, by
                // `MethodOrLoopContractCompiler.visitLoop`'s wrongContract
                // check -- cases are method contracts.)
                List<AstNode<StmtExpr>> invariants = new ArrayList<>();
                boolean savedContractContext = inContractContext;
                inContractContext = true;
                try {
                    for (var inv : loopContract.loopInvariants()) {
                        invariants.add(node(convertExpression(inv.get(), renames), inv.get()));
                    }
                } finally {
                    // Restored before the loop BODY is converted: the body is not a
                    // contract position, so an allocation there is fine.
                    inContractContext = savedContractContext;
                }
                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
                List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                for (var s : implStatements) {
                    StmtExpr converted = convertStatement(s, renames);
                    if (converted != null) stmts.add(node(converted, s));
                }
                return new LoopParts(invariants, new StmtExpr.Block(stmts, Optional.empty()));
            } else {
                return new LoopParts(List.of(), convertStatementOrEmpty(body, renames));
            }
        }

        /**
         * Whether {@code sym} is a non-static field whose value is not a
         * compile-time constant, i.e. heap state reached through a receiver. A
         * constant folds to a literal upstream; a static field is not heap state.
         */
        private static boolean isHeapInstanceField(Symbol sym) {
            return sym instanceof Symbol.VarSymbol varSym
                    && varSym.getKind() == javax.lang.model.element.ElementKind.FIELD
                    && (varSym.flags() & Flags.STATIC) == 0
                    && varSym.getConstValue() == null;
        }

        private Variable convertVariable(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCIdent ident when isHeapInstanceField(ident.sym) -> {
                    // Bare `x = ...` in an instance body is an implicit
                    // `this.x = ...`: heap state, not a local, so it must go through
                    // the receiver or Strata reports 'x' is not defined.
                    ensureFieldOnComposite((Symbol.VarSymbol) ident.sym);
                    yield new Variable.Field(
                            node(new StmtExpr.Var(new Variable.Local(ident(LAUREL_SELF_BINDING)))),
                            ident(ident.name.toString()));
                }
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield new Variable.Local(identForPossiblyRenamed(name, renames));
                }
                case JCTree.JCFieldAccess fa when isHeapInstanceField(fa.sym) -> {
                    // `this.x = ...` / `obj.x = ...`: declare the field on its owning
                    // composite so heap parameterization resolves the write.
                    ensureFieldOnComposite((Symbol.VarSymbol) fa.sym);
                    yield new Variable.Field(node(convertExpression(fa.selected, renames)),
                            ident(fa.name.toString()));
                }
                case JCTree.JCFieldAccess fa ->
                    new Variable.Field(node(convertExpression(fa.selected, renames)), ident(fa.name.toString()));
                case JCTree.JCParens parens -> convertVariable(parens.expr, renames);
                default -> throw new JavaViolationException("Unsupported variable expression: " + expr.getClass().getSimpleName());
            };
        }



        /**
         * The receiver for an instance call: the converted receiver expression, or
         * the enclosing {@code self?} binder for an implicit-this {@code m(...)}.
         *
         * <p>{@code super.m(...)} is refused: resolution walks the receiver's
         * declared type upward, so an {@code obj#method} call cannot express "the
         * parent's {@code m}, skipping the override".
         */
        /** The receiver expression for an instance call. Whether the receiver SHAPE is
            supported is decided by {@link RefusalGates#receiverRefusal}, enforced at the
            call site before this runs; this method only emits. */
        private StmtExpr receiverSelf(JCTree.JCExpression methodSelect, Map<String, String> renames) {
            if (methodSelect instanceof JCTree.JCFieldAccess fieldAccess) {
                return convertExpression(fieldAccess.selected, renames);
            }
            // Implicit-this call `m(...)`: the receiver is the enclosing instance.
            return new StmtExpr.Var(new Variable.Local(ident(LAUREL_SELF_BINDING)));
        }



        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident when ident.name == ident.name.table.names._this ->
                    // `this` in an instance body is the explicit receiver parameter.
                    new StmtExpr.Var(new Variable.Local(ident(LAUREL_SELF_BINDING)));
                case JCTree.JCIdent ident when isHeapInstanceField(ident.sym) -> {
                    // Implicit `this.x` (see the matching arm in convertVariable).
                    ensureFieldOnComposite((Symbol.VarSymbol) ident.sym);
                    yield new StmtExpr.Var(new Variable.Field(
                            node(new StmtExpr.Var(new Variable.Local(ident(LAUREL_SELF_BINDING)))),
                            ident(ident.name.toString())));
                }
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield varForPossiblyRenamed(name, renames);
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        new StmtExpr.Assign(List.of(nodeVar(convertVariable(asgn.lhs, renames))),
                                node(convertExpression(asgn.rhs, renames)));
                case JCTree.JCAssignOp asgnOp -> convertAssignOp(asgnOp, renames);
                case JCTree.JCConditional cond ->
                        new StmtExpr.IfThenElse(node(convertExpression(cond.cond, renames), cond.cond),
                                node(convertExpression(cond.truepart, renames), cond.truepart),
                                Optional.of(node(convertExpression(cond.falsepart, renames), cond.falsepart)));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    // A PURE opted-out callee is exempt: it now keeps an opaque
                    // DECLARATION (see `declarationOnly`) so the name resolves, and
                    // overload siblings no longer share a Laurel name because an
                    // overloaded name carries its erased parameter types (see
                    // `overloadSuffix`). The caller stays verifiable and simply learns
                    // nothing about the result -- which is the documented intent of
                    // `@Verify(false) @Pure` (see PureUnverifiedMethod).
                    if (optedOutMethods.contains(methodSym) && !jverifyUtils.isPure(methodSym)) {
                        // The callee emits no procedure, but its mangled name is not
                        // unique to it: an overload sibling that IS emitted mangles to
                        // the same Laurel name, so this call would bind the sibling's
                        // contract -- wrong contract, possibly wrong arity. Refuse the
                        // caller instead.
                        //
                        // Caught HERE rather than left to the post-loop scan, though
                        // both consult the same `skippedMethodSymbols()` set: opt-out
                        // is known from the annotation snapshot before translation, so
                        // refusing now aborts this caller before any procedure exists
                        // to retract, and the existing catch site reports it. The scan
                        // detects the call too, but only after the caller is translated,
                        // so it has to drop the procedure afterwards. Removing
                        // this check makes CallToOptedOutOverload fail (verified).
                        throw new JavaViolationException(
                                "call to '" + methodSym.name
                                        + "', which could not be translated");
                    }
                    boolean calleeStatic = (methodSym.flags() & Flags.STATIC) != 0;
                    if (calleeStatic) {
                        // A static mangles to `Class?name?sig`, so overloaded
                        // statics get distinct names; no refusal needed.
                    }
                    StmtExpr instanceReceiver = null;
                    if (!calleeStatic) {
                        // Receiver-shape refusals are reported BEFORE the general
                        // dispatch refusal, so `super.m()` and `new T().m()` name their own
                        // cause rather than the less specific "polymorphic dispatch". That
                        // ordering used to be implicit in `receiverSelf` throwing first.
                        RefusalGates.enforce(
                                RefusalGates.receiverRefusal(invocation.getMethodSelect()));
                        StmtExpr selfArg = receiverSelf(invocation.getMethodSelect(), renames);
                        // No dispatch refusal: Laurel dispatches an overridden
                        // composite method on the receiver's runtime tag
                        // (LiftInstanceProcedures) and CheckOverrideRefinement
                        // discharges the Liskov obligation that makes reasoning
                        // against the declared type's contract sound. See
                        // PolyDispatchSoundness, which pins both halves.
                        // No overload refusal either: `obj#method` re-resolves BY
                        // NAME in Strata, and the emitted name carries the erased
                        // signature (overloadSuffix), so an overload set spanning
                        // the ancestry no longer makes the choice ambiguous -- an
                        // override keeps the base's name, an overload gets its own.
                        instanceReceiver = selfArg;
                    }
                    List<AstNode<StmtExpr>> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(node(convertExpression(arg, renames), arg));
                    }
                    if (instanceReceiver == null) {
                        calledStaticMethods.add(methodSym);
                        yield new StmtExpr.StaticCall(ident(qualifiedMethodName(methodSym)), args, List.of());
                    }
                    // `obj#method(args)`: LiftInstanceProcedures rewrites this to the
                    // lifted `<Composite>$<method>`, prepending the receiver (see
                    // translateMethod's `self?` parameter).
                    yield new StmtExpr.InstanceCall(
                            node(instanceReceiver, invocation.getMethodSelect()),
                            ident(methodSym.name.toString() + overloadSuffix(methodSym)), args);
                }
                case JCTree.LetExpr letExpr -> {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var def : letExpr.defs) {
                        StmtExpr converted = convertStatement(def, renames);
                        if (converted != null) stmts.add(node(converted, def));
                    }
                    stmts.add(node(convertExpression(letExpr.expr, renames)));
                    yield new StmtExpr.Block(stmts, Optional.empty());
                }
                case JCTree.JCFieldAccess fa when fa.type.constValue() != null ->
                    convertConstantValue(fa.type.getTag(), fa.type.constValue());
                // Instance-field read: `obj.field`. Strata's heap parameterization
                // pass rewrites this into a `readField(heap, obj, "Owner.field")`
                // call using the owning composite's field declaration.
                case JCTree.JCFieldAccess fa when fa.sym instanceof Symbol.VarSymbol vs
                        && (vs.flags() & Flags.STATIC) == 0 -> {
                    StmtExpr obj = convertExpression(fa.selected, renames);
                    ensureFieldOnComposite(vs);
                    yield new StmtExpr.Var(new Variable.Field(
                            node(obj, fa.selected), ident(fa.name.toString())));
                }
                case JCTree.JCNewClass newClass when inContractContext ->
                    // `new` -- and a lambda, which lowers to `new <anon>()` -- produces
                    // a heap-allocating block. Strata lifts such blocks out of bodies
                    // but not out of contract expressions, where they reach Core
                    // unlowered ("block expression should have been lowered"). Refuse
                    // for a graceful skip until Strata lifts inside contracts.
                    refuseAllocationInContract();
                case JCTree.JCNewClass newClass -> {
                    String name = compositeSortName(newClass.type.tsym);
                    referencedCompositeTypes.putIfAbsent(name, newClass.type.tsym);
                    for (var arg : newClass.args) {
                        convertExpression(arg, renames);
                    }
                    // `new GenericContainer<DummySuper>(..)` carries its type
                    // arguments so the instantiation monomorphizes; a raw or
                    // library `new` leaves them empty.
                    List<AstNode<HighType>> newTypeArgs = new ArrayList<>();
                    if (newClass.type instanceof com.sun.tools.javac.code.Type.ClassType nct
                            && isModeledComposite(nct.tsym)) {
                        for (var arg : nct.getTypeArguments()) {
                            newTypeArgs.add(node(translateType(arg)));
                        }
                    }
                    yield new StmtExpr.New(ident(name), newTypeArgs);
                }
                case JCTree.JCInstanceOf instanceOf -> {
                    // A plain type test maps onto Laurel's `is`. This used to be
                    // restricted to exception types, on the grounds that other
                    // reference types were "opaque sorts with no declared hierarchy,
                    // so `is` on them would be vacuous". That is no longer true:
                    // emitComposite declares every sort's FULL supertype closure
                    // (from getSuperclass() + getInterfaces()), and Laurel keeps a
                    // runtime type tag per composite, so `is` decides properly for
                    // any program composite. Registering the sort is enough to get
                    // its hierarchy emitted.
                    if (!(instanceOf.pattern instanceof JCTree.JCBindingPattern)
                            && instanceOf.pattern.type instanceof com.sun.tools.javac.code.Type.ClassType ct
                            && ct.tsym instanceof Symbol.ClassSymbol cls) {
                        // The `is` type slot is a bare identifier -- a generic
                        // instantiation there is a parse error on the Laurel side --
                        // so a parameterised target still refuses.
                        if (!ct.getTypeArguments().isEmpty()) {
                            throw new JavaViolationException(
                                    "instanceof a generic type is not yet supported");
                        }
                        // Only a type THIS compilation declares. A library type is
                        // loaded from a classfile (sourcefile == null) and several --
                        // String, the boxed primitives -- are modelled as Laurel
                        // values rather than composites, so registering one as a
                        // composite sort here would collide with that encoding.
                        if (cls.sourcefile == null
                                || !ownSourceFiles.contains(cls.sourcefile)) {
                            throw new JavaViolationException(
                                    "instanceof on opaque reference types is not yet supported");
                        }
                        if (isThrowableSubtype(cls)) {
                            registerHierarchyUpToThrowable(cls);
                        }
                        String sortName = compositeSortName(cls);
                        referencedCompositeTypes.putIfAbsent(sortName, cls);
                        yield new StmtExpr.IsType(
                                node(convertExpression(instanceOf.expr, renames), instanceOf.expr),
                                node(new HighType.UserDefined(ident(sortName)),
                                        typeRangeOrNone(instanceOf.pattern)));
                    }
                    throw new JavaViolationException(
                        "instanceof on opaque reference types is not yet supported");
                }
                case JCTree.JCTypeCast cast -> {
                    // A reference DOWNCAST is Laurel's `as`, whose embedded
                    // type-test assertion Strata discharges -- from an
                    // `instanceof` antecedent in the per-type claim idiom, or as a
                    // genuine obligation where there is none. Exception types took
                    // this path already; it generalises to any composite this
                    // compilation declares, now that every sort carries its
                    // supertype closure and a runtime tag. An UPCAST is not here:
                    // it needs no check, so convertTypeCast returns the operand.
                    if (cast.type instanceof com.sun.tools.javac.code.Type.ClassType ct
                            && ct.tsym instanceof Symbol.ClassSymbol cls
                            && (isThrowableSubtype(cls) || isOwnDowncast(cast, ct, cls))) {
                        if (isThrowableSubtype(cls)) {
                            registerHierarchyUpToThrowable(cls);
                        }
                        String sortName = compositeSortName(cls);
                        referencedCompositeTypes.putIfAbsent(sortName, cls);
                        yield new StmtExpr.AsType(
                                node(convertExpression(cast.expr, renames), cast.expr),
                                node(new HighType.UserDefined(ident(sortName)),
                                        typeRangeOrNone(cast.clazz)));
                    }
                    yield convertTypeCast(cast, renames);
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return convertConstantValue(literal.typetag, literal.value);
        }

        /// A Java type cast (explicit or implicit) — `(T) x`. Emitted as a
        /// call to a helper procedure:
        ///
        ///   (T) x    →    java?cast_to_T$_from_<S>(x)
        ///
        /// where `<S>` is the source type. The helper is declared once at
        /// file scope with `requires T$constraint(x)` and
        /// `ensures $result == x`. The call's precondition becomes a proof
        /// obligation at the cast site; for widening it's trivially
        /// satisfied, for narrowing it's the real proof obligation. This
        /// mirrors how JVerify already treats in-type overflow (e.g.
        /// `Integer.MAX_VALUE + 1` fails the `int32` range check instead
        /// of silently wrapping). Design: `docs/jccast-design.md`.
        ///
        /// We emit a call rather than an `AsType` because Strata's
        /// `LaurelToCoreSchemaPass` has no translation for expression-
        /// position `AsType`. Calls are handled uniformly in body and
        /// contract positions, so the same cast semantics apply inside
        /// `precondition(...)` and `postcondition(...)` as well.
        ///
        /// Since Laurel does not support polymorphism, we generate one
        /// helper per (source, target) type pair on demand and stash the
        /// resulting Procedure into `castHelpers` so it can be emitted at
        /// file scope alongside `getPredefinedTypes()`.
        private StmtExpr convertTypeCast(JCTree.JCTypeCast cast, Map<String, String> renames) {
            StmtExpr operand = convertExpression(cast.expr, renames);
            HighType targetType = translateType(cast.type);
            HighType sourceType = translateType(cast.expr.type);
            // Restrict to primitive integer targets (the CR's stated scope):
            // one of the built-in constrained integer types (int8/16/32/64,
            // char, nat*) or the raw `int` for `@Unbounded int` / `@Unbounded
            // @Nat int`. `translateType` maps every class/interface reference
            // to a UserDefined type too, and for those the helper would need to
            // emit `ensures $result == x` where the two sides have different
            // composite sorts -- Strata rejects the '==' at resolution,
            // surfacing both at the helper's source-less range and at the
            // user's cast site. Fall through to the unsupported-expression
            // path instead so users see a single clean diagnostic.
            String targetName = laurelIntegerTypeName(targetType);
            if (targetName == null) {
                // A reference UPCAST is the identity in Laurel: every composite
                // declares its full supertype closure as `extends` edges, so a
                // subtype value already flows wherever the supertype is
                // expected and no conversion is needed. `(Object) someFoo` is
                // the common case. A DOWNCAST is a runtime question and still
                // refuses -- it would need Laurel's `as` / downcast$C helper.
                if (cast.expr.type != null && cast.type != null
                        && !cast.expr.type.isPrimitive() && !cast.type.isPrimitive()
                        && javacTypes.isSubtype(
                                javacTypes.erasure(cast.expr.type),
                                javacTypes.erasure(cast.type))) {
                    return operand;
                }
                throw new JavaViolationException(
                        "Cast to " + cast.type + " is not yet supported");
            }
            String sourceName = laurelTypeName(sourceType);
            String helperName = "java?cast_to_" + targetName + "_from_" + sourceName;
            registerCastHelper(helperName, sourceName, sourceType, targetName, targetType);
            // Stamp the operand with its *own* tree, not this cast's: for a
            // chained cast like `(byte) (int) l` the inner cast's diagnostic
            // must point at `(int) l`, not at the whole `(byte) (int) l`.
            return new StmtExpr.StaticCall(ident(helperName),
                    List.of(node(operand, cast.expr)), List.of());
        }

        /// Returns the mangling name for {@code targetType} if it is one of
        /// the primitive-integer targets this pass handles, else {@code null}.
        /// Accepted targets:
        ///   * UserDefined whose name is a known constrained-integer sort
        ///     (`int8`/`int16`/`int32`/`int64`/`char`/`nat7`/`nat15`/`nat31`/
        ///     `nat63`/`nat`)
        ///   * TInt (the raw Laurel int, used for `@Unbounded int`)
        private String laurelIntegerTypeName(HighType targetType) {
            if (targetType instanceof HighType.UserDefined u
                    && boundsFor(u.name().text()) != null) {
                return u.name().text();
            }
            if (targetType instanceof HighType.TInt) {
                return "int";
            }
            return null;
        }

        /// Name of a Laurel type for helper-name mangling. UserDefined
        /// carries its name directly; the raw `TInt` is spelled
        /// `int` (for `@Unbounded int`).
        private String laurelTypeName(HighType ty) {
            if (ty instanceof HighType.UserDefined u) return u.name().text();
            if (ty instanceof HighType.TInt) return "int";
            throw new JavaViolationException("Unsupported cast source type: " + ty);
        }

        /// Register (once) the helper procedure for a given cast:
        ///
        ///   procedure <helperName>(x: <sourceType>): <targetName>
        ///     requires <inline bounds for targetName>  summary "cast from <S> to <T>"
        ///     opaque(ensures $result == x) { return x }
        ///
        /// The `requires` bounds are inlined (rather than calling
        /// `<targetName>$constraint(x)`) because that constraint helper is
        /// generated by Strata's `ConstrainedTypeElim` pass, which runs
        /// AFTER the initial resolve — so a call to it in frontend-emitted
        /// Laurel would fail resolution.
        ///
        /// Each precondition carries a `summary` string. Strata's
        /// Verifier renders it as `"<summary> does not hold"` at
        /// failure sites, so the diagnostic reads "cast from int to short
        /// does not hold" rather than the generic "precondition does not
        /// hold".
        ///
        /// `Body.Opaque` keeps the postcondition as what callers see; the
        /// `return x` implementation is what an interpreter runs.
        private void registerCastHelper(String helperName, String sourceName,
                                        HighType sourceType, String targetName,
                                        HighType targetType) {
            if (castHelpers.containsKey(helperName)) return;
            var x = var_("x");
            Long[] bounds = boundsFor(targetName);
            var castLabel = Optional.of("cast from " + sourceName + " to " + targetName);
            var preconditions = new ArrayList<Condition>();
            if (bounds != null) {
                if (bounds[0] != null) {
                    preconditions.add(new Condition(
                            node(primitiveOp(new Operation.Geq(), node(x), node(longLiteral(bounds[0])))),
                            castLabel, new ConditionMode.Both()));
                }
                if (bounds[1] != null) {
                    preconditions.add(new Condition(
                            node(primitiveOp(new Operation.Leq(), node(x), node(longLiteral(bounds[1])))),
                            castLabel, new ConditionMode.Both()));
                }
            }
            // `@Unbounded int` targets have no bounds check at all (widening
            // from any bounded source is unconditionally sound); preconditions
            // is left empty and only the postcondition pins `$result == x`.
            var resultEqX = primitiveOp(new Operation.Eq(),
                    node(reservedVar_(LAUREL_RESULT_BINDING)), node(x));
            var postconditions = List.of(
                    new Condition(node(resultEqX), Optional.empty(), new ConditionMode.Both()));
            var proc = new Procedure(
                    ident(helperName), /*typeArgs=*/List.of(),
                    List.of(new Parameter(ident("x"), node(sourceType))),
                    List.of(new Parameter(reservedIdent(LAUREL_RESULT_BINDING), node(targetType))),
                    preconditions,
                    new CoroutineContracts.Regular(),
                    /*decreases=*/ Optional.empty(),
                    new Body.Opaque(postconditions,
                            Optional.of(node(new StmtExpr.Block(
                                    List.of(node(new StmtExpr.Return(Optional.of(node(x))))), Optional.empty()))),
                            unguardedModifies(List.of())),
                    /*invokeOn=*/ Optional.empty(),
                    /*isInterpretEntry=*/ false,
                    /*axioms=*/ List.of(),
                    // Cast helpers are total: they never take the exceptional exit,
                    // so no declared throws type and no `throwsOn` behaviour cases.
                    /*throwsType=*/ Optional.empty(), /*throwsBinding=*/ Optional.empty(),
                    /*throwsOn=*/ List.of(),
                    /*readsGlobals=*/ List.of(), /*writesGlobals=*/ List.of());
            castHelpers.put(helperName, proc);
        }

        /// Bounds for the built-in constrained integer types, as declared
        /// in the shared {@link JavaToLaurelCompiler#BOUNDS} table. Returns
        /// {@code {min, max}} (a {@code null} element means "unbounded on
        /// that side"), or {@code null} if the target isn't a built-in
        /// constrained-integer sort.
        private Long[] boundsFor(String targetName) {
            return BOUNDS.get(targetName);
        }

        /// Lower a primitive compile-time constant value to a Laurel literal.
        /// Used by JCLiteral and JCFieldAccess (where the value is attached
        /// via Type.constValue()). Boolean values arrive as Integer 0/1 from
        /// Type.constValue() — Java represents booleans as int internally.
        private StmtExpr convertConstantValue(TypeTag tag, Object v) {
            return switch (tag) {
                case BOOLEAN -> new StmtExpr.LiteralBool(((Number) v).intValue() != 0);
                case CHAR, INT, SHORT, BYTE, LONG -> longLiteral(((Number) v).longValue());
                // A String literal is a constant whose tag is CLASS. Reachable now
                // that instance calls are translated: the receiver of
                // `"abc".length()` is such a constant, and previously aborted the
                // enclosing method with "Unsupported constant type tag: CLASS".
                case CLASS -> {
                    String s = String.valueOf(v);
                    // A Java string can hold unpaired surrogates ('\uD800' with no
                    // low surrogate); those have no UTF-8 encoding, so serializing
                    // the Laurel literal to Ion would throw. Refuse cleanly instead
                    // of crashing the run (see Strings.stringLiteralMispairedSurrogates).
                    if (!isEncodableUtf16(s)) {
                        throw new JavaViolationException(
                                "string literal with unpaired surrogates is not supported");
                    }
                    yield new StmtExpr.LiteralString(s);
                }
                default -> throw new JavaViolationException("Unsupported constant type tag: " + tag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            AstNode<StmtExpr> lhs = node(convertExpression(binary.lhs, renames), binary.lhs);
            AstNode<StmtExpr> rhs = node(convertExpression(binary.rhs, renames), binary.rhs);
            Operation op = switch (binary.getTag()) {
                case PLUS -> new Operation.Add();
                case MINUS -> new Operation.Sub();
                case MUL -> new Operation.Mul();
                case DIV -> new Operation.DivT();
                case MOD -> new Operation.ModT();
                case EQ -> new Operation.Eq();
                case NE -> new Operation.Neq();
                case GT -> new Operation.Gt();
                case LT -> new Operation.Lt();
                case GE -> new Operation.Geq();
                case LE -> new Operation.Leq();
                case AND -> new Operation.AndThen();
                case OR -> new Operation.OrElse();
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
            return primitiveOp(op, lhs, rhs);
        }

        /**
         * A compound assignment {@code lhs op= rhs}, desugared to
         * {@code lhs = lhs op rhs}.
         *
         * <p>Restricted to a LOCAL-variable or parameter LHS. A field LHS -- an
         * explicit {@code obj.f} (JCFieldAccess) or a bare unqualified field
         * ({@code counter += 1}, still a JCIdent) -- reads its receiver / heap
         * state twice under this desugaring, so it stays refused as before; a
         * static-field write in particular is not supported at all yet
         * (CascadeDepth3 depends on that refusal).
         */
        private StmtExpr convertAssignOp(JCTree.JCAssignOp assignOp, Map<String, String> renames) {
            if (!(assignOp.lhs instanceof JCTree.JCIdent ident)
                    || !(TreeInfo.symbol(ident) instanceof Symbol.VarSymbol varSym)
                    || !(varSym.owner instanceof Symbol.MethodSymbol)) {
                throw new JavaViolationException("Unsupported expression: JCAssignOp");
            }
            Operation op = switch (assignOp.getTag()) {
                case PLUS_ASG -> new Operation.Add();
                case MINUS_ASG -> new Operation.Sub();
                case MUL_ASG -> new Operation.Mul();
                case DIV_ASG -> new Operation.DivT();
                case MOD_ASG -> new Operation.ModT();
                default -> throw new JavaViolationException("Unsupported expression: JCAssignOp");
            };
            AstNode<StmtExpr> current = node(convertExpression(assignOp.lhs, renames), assignOp.lhs);
            AstNode<StmtExpr> rhs = node(convertExpression(assignOp.rhs, renames), assignOp.rhs);
            AstNode<StmtExpr> combined = node(primitiveOp(op, current, rhs), assignOp);
            return new StmtExpr.Assign(
                    List.of(nodeVar(convertVariable(assignOp.lhs, renames))), combined);
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            AstNode<StmtExpr> inner = node(convertExpression(unary.arg, renames), unary.arg);
            return switch (unary.getTag()) {
                case NOT -> primitiveOp(new Operation.Not(), inner);
                case NEG -> primitiveOp(new Operation.Neg(), inner);
                // Unary plus is the identity. Any numeric promotion it implies
                // (e.g. +byte -> int) was already applied by javac at
                // attribution, so `inner` carries the promoted value and the
                // operator itself adds nothing.
                case POS -> inner.val();
                case PREINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case PREDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                default -> throw new JavaViolationException("Unsupported unary op: " + unary.getTag());
            };
        }

        /**
         * A call on a {@code JVerify.Map} or {@code JVerify.IntMap} value, lowered to the
         * Laurel map prelude. {@code IntMap} is the TOTAL map, so {@code get} is
         * {@code select}; the generic {@code Map} is the partial one over {@code $MapEntry},
         * so {@code get}/{@code contains} are {@code mapGet}/{@code mapContains}, which carry
         * bodies over {@code select}. All are transparent, so two reads of one key agree --
         * which a call to an opaque procedure would not give. The type arguments follow from
         * the map argument, so the call states none. {@code size()} has no Laurel counterpart
         * and stays refused.
         */
        private StmtExpr convertMapCall(JCTree.JCMethodInvocation invocation, String name,
                                        boolean isTotal, Map<String, String> renames) {
            if (!(invocation.getMethodSelect() instanceof JCTree.JCFieldAccess access)) {
                throw new JavaViolationException("JVerify map " + name + " needs a receiver");
            }
            var callee = isTotal
                    ? switch (name) {
                        case "get" -> "select";
                        default -> throw new JavaViolationException(
                                "Unsupported JVerify.IntMap method: " + name);
                    }
                    : switch (name) {
                        case "get" -> "mapGet";
                        case "contains" -> "mapContains";
                        default -> throw new JavaViolationException(
                                "Unsupported JVerify.Map method: " + name);
                    };
            if (invocation.args.size() != 1) {
                throw new JavaViolationException(
                        "JVerify map " + name + " takes a single argument");
            }
            // No typeArgs: `select`/`mapGet`/`mapContains` are generic in the prelude and
            // Laurel infers K and V from the map argument.
            return new StmtExpr.StaticCall(ident(callee, invocation),
                    List.of(node(convertExpression(access.selected, renames), access.selected),
                            node(convertExpression(invocation.args.getFirst(), renames),
                                    invocation.args.getFirst())),
                    List.of());
        }

        /**
         * The method OWNER is the DECLARING type, so a call through a user subtype
         * ({@code interface StrMap extends JVerify.Map<String, String>}) resolves to the very
         * same {@code JVerify.Map} symbol -- while its receiver is not a map at all: a subtype
         * is not {@code JVERIFY_MAP}/{@code JVERIFY_INT_MAP}, so {@code translateType} lowers it
         * to a composite and the map prelude call fails to resolve against it. Refuse on the
         * receiver's static type instead, which keeps the diagnostic on the call site.
         */
        private void requireMapReceiver(JCTree.JCMethodInvocation invocation, String name) {
            if (!(invocation.getMethodSelect() instanceof JCTree.JCFieldAccess access)) {
                return;
            }
            var receiverType = access.selected.type;
            var receiverName = receiverType == null || receiverType.tsym == null
                    ? null
                    : receiverType.tsym.getQualifiedName().toString();
            if (!JVERIFY_MAP.equals(receiverName) && !JVERIFY_INT_MAP.equals(receiverName)) {
                throw new JavaViolationException("JVerify map " + name
                        + " needs a JVerify.Map or JVerify.IntMap receiver, found: "
                        + (receiverName == null ? receiverType : receiverName));
            }
        }

        private StmtExpr convertJVerifyCall(JCTree.JCMethodInvocation invocation,
                                            Symbol.MethodSymbol jverifyMethod,
                                            Map<String, String> renames) {
            var name = jverifyMethod.getQualifiedName().toString();
            if (jverifyMethod.owner instanceof Symbol.ClassSymbol owner) {
                var ownerName = owner.getQualifiedName().toString();
                if (JVERIFY_MAP.equals(ownerName) || JVERIFY_INT_MAP.equals(ownerName)) {
                    requireMapReceiver(invocation, name);
                    return convertMapCall(invocation, name,
                            JVERIFY_INT_MAP.equals(ownerName), renames);
                }
            }
            return switch (name) {
                case "check" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("check should have a single argument");
                    yield new StmtExpr.Assert(node(convertExpression(invocation.args.getFirst(), renames)), Optional.empty());
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield new StmtExpr.Assume(node(convertExpression(invocation.args.getFirst(), renames)));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    var notA = primitiveOp(new Operation.Not(), node(convertExpression(invocation.args.get(0), renames)));
                    yield primitiveOp(new Operation.Or(), node(notA), node(convertExpression(invocation.args.get(1), renames)));
                }
                // `jequals(a, b)` is observational equality: `==` on primitives,
                // reference equality on impure composites, structural equality on
                // pure ones. Laurel's `Eq` is defined with exactly those semantics
                // (see LaurelAST.lean `Operation.Eq`), so this lowers to the same
                // node `convertBinary` emits for Java `==`. `$eq` carries a
                // placeholder `int -> int -> bool` signature in
                // CoreDefinitionsForLaurel, but resolution special-cases it to
                // require only operand consistency and Core lowers it to
                // polymorphic equality, so composite and bool operands are fine.
                //
                // Limitation: the "structural equality on pure ones" case is not
                // realized yet. Records are pure but currently lower to opaque
                // composites (translateType), so `Eq` on them is rewritten to
                // reference equality -- structurally-equal records compare as
                // distinct, which is unsound in the negation direction. Pinned by
                // tests/javasupport/Jequals.java; resolved once records lower to a
                // value/record ("Frozen") type rather than a composite.
                case "jequals" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("jequals should have two arguments");
                    yield primitiveOp(new Operation.Eq(),
                            node(convertExpression(invocation.args.get(0), renames)),
                            node(convertExpression(invocation.args.get(1), renames)));
                }
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr qBody = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
                        refuseReservedName(p.name);
                        var param = new Parameter(ident(p.name.toString()),
                                translateTypeWithRange(p.type, p.mods, typeRangeOrNone(p.vartype)));
                        var mode = name.equals("forall")
                                ? (QuantifierMode) new QuantifierMode.Forall()
                                : new QuantifierMode.Exists();
                        qBody = new StmtExpr.Quantifier(mode, param, Optional.empty(), node(qBody));
                    }
                    yield qBody;
                }
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}
