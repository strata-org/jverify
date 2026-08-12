package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.*;
import org.strata.jverify.verifier.VerifierOptions;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.frontend.JavaLowerer;
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
import java.util.Optional;

public class JavaToLaurelCompiler {
    /// Since Strata commit 72f3a6e the short `: T` return form auto-generates
    /// an output parameter named `$result` (a reserved name), not `result`.
    private static final String LAUREL_RESULT_BINDING = "$result";
    /// Synthesized name binding the thrown value in a procedure's `throws (e: T)`
    /// clause. Referenced by `throwsOn` case postconditions once the exceptional
    /// contract DSL lands; until then it is declared and unused.
    private static final String THROWN_BINDING = "$thrown";

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
    /// javac's synthetic source path for trees injected by a simplification
    /// pass; Strata reports diagnostics for them against this URI, which has
    /// no entry in our line map.
    private static final String SYNTHETIC_UNKNOWN_PATH = "/<unknown>";

    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    private final Reporter reporter;
    private final VerifyAnnotationCompiler annotationCompiler;
    JCTree.JCCompilationUnit currentCompilationUnit;

    private final Set<String> referencedCompositeTypes = new LinkedHashSet<>();

    /// (helperName -> Procedure) for every cast helper referenced anywhere
    /// in the program. Lives at the whole-program scope (not per file)
    /// because all files share one Laurel program scope: emitting the
    /// same `$cast_to_<T>_from_<S>` helper in two files makes Strata
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
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        annotationCompiler = VerifyAnnotationCompiler.instance(context);
    }

    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

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

    private static Identifier ident(String name) {
        return new Identifier(name, Optional.empty(), UNKNOWN_RANGE);
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
        if (endPos == -1) endPos = startPos;
        var filePath = currentCompilationUnit.sourcefile.toUri().toString();
        return new FileRange(new Uri(filePath), new SourceRange(new Raw(startPos), new Raw(endPos)));
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
        return new StmtExpr.StaticCall(ident(operationProcName(op)), List.of(args));
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
            for (var typeName : referencedCompositeTypes) {
                List<Field> fields = compositeFields.containsKey(typeName)
                        ? new ArrayList<>(compositeFields.get(typeName).values())
                        : List.of();
                List<Identifier> extending = compositeExtends.containsKey(typeName)
                        ? List.of(ident(compositeExtends.get(typeName)))
                        : List.of();
                composites.add(new TypeDefinition.Composite(
                        new CompositeType(ident(typeName), extending, fields, List.of())));
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
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            String name = classType.tsym.getQualifiedName().toString();
            String sortName = name.replace('$', '.');
            referencedCompositeTypes.add(sortName);
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
    private static String compositeSortName(Symbol.ClassSymbol cls) {
        return cls.getQualifiedName().toString().replace('$', '.');
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
        referencedCompositeTypes.add(joinSort);
        return new DeclaredThrows(join,
                node(new HighType.UserDefined(ident(joinSort)),
                        typeRangeOrNone(thrownTrees.getFirst())));
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
    private void registerHierarchyUpToThrowable(Symbol.ClassSymbol cls) {
        Symbol.ClassSymbol stop = cls;
        for (Symbol.ClassSymbol c = cls; c != null; c = superclassOf(c)) {
            stop = c;
            if (c.getQualifiedName().contentEquals("java.lang.Throwable")) {
                break;
            }
        }
        registerHierarchyUpTo(cls, stop);
        referencedCompositeTypes.add(compositeSortName(stop));
    }

    /**
     * Record that composite {@code child} extends {@code parent}, declaring
     * both sorts. Membership in {@link #referencedCompositeTypes} and the edge
     * in {@link #compositeExtends} always move together through this method,
     * so a linked child cannot silently miss its declaration (or dangle).
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
        referencedCompositeTypes.add(childSort);
        referencedCompositeTypes.add(parentSort);
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
        referencedCompositeTypes.add(sortName);
        LinkedHashMap<String, Field> fields = compositeFields.computeIfAbsent(
                sortName, k -> new LinkedHashMap<>());
        String name = fieldSym.name.toString();
        if (fields.containsKey(name)) return;
        HighType fieldType = translateType(fieldSym.type);
        // `isMutable` replaces the old immutableField/mutableField pair: a
        // `final` Java field is not mutable.
        boolean isMutable = (fieldSym.flags() & Flags.FINAL) == 0;
        fields.put(name, new Field(ident(name), isMutable, node(fieldType)));
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

    private static String qualifiedMethodName(Symbol.MethodSymbol sym) {
        // `outermostClass()` walks the owner chain to the first ClassSymbol,
        // casting along the way; some invocations (record accessors of types
        // declared inside an anonymous class, or built-ins on synthetic Symtab
        // entries) reach here with a non-ClassSymbol owner and throw
        // ClassCastException. Fall back to the immediate owner in that case.
        Symbol.ClassSymbol outer;
        try {
            outer = sym.outermostClass();
        } catch (ClassCastException e) {
            outer = null;
        }
        if (outer != null) {
            // Fully-qualified (package-included) name, sanitised like the
            // CompositeType sort names ('$' -> '.'), so two same-named classes
            // in different packages don't produce colliding procedure names.
            return outer.getQualifiedName().toString().replace('$', '.') + "_" + sym.name;
        }
        Symbol owner = sym.owner;
        String prefix = (owner != null && owner.name != null)
                ? owner.name.toString()
                : "$unknown";
        return prefix + "_" + sym.name;
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();
        private int labelCounter = 0;

        private record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {}
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

                private boolean writesInstanceField(JCTree.JCExpression lhs) {
                    return lhs instanceof JCTree.JCFieldAccess fa
                            && fa.sym instanceof Symbol.VarSymbol vs
                            && (vs.flags() & Flags.STATIC) == 0;
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
            if ((method.mods.flags & Flags.STATIC) != 0) {
                try {
                    translateStaticMethod(method);
                } catch (JavaViolationException e) {
                    reporter.reportError(method, "translatorError", e.getMessage());
                    annotationCompiler.markSkipped(currentCompilationUnit, method);
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

        private void translateStaticMethod(JCTree.JCMethodDecl method) {
            String methodName = qualifiedMethodName(method.sym);

            List<Parameter> inputs = new ArrayList<>();
            for (var param : method.params) {
                inputs.add(new Parameter(ident(param.name.toString()),
                        translateTypeWithRange(param.type, param.mods, typeRangeOrNone(param.vartype))));
            }

            List<Parameter> outputs = new ArrayList<>();
            if (method.restype != null && method.restype.type != null
                    && method.restype.type.getTag() != TypeTag.VOID) {
                outputs.add(new Parameter(ident(LAUREL_RESULT_BINDING),
                        translateTypeWithRange(method.restype.type, null, typeRangeOrNone(method.restype))));
            }

            // Exceptional exit: the Java `throws` clause is the single source of
            // the declared exceptional type. The
            // binding name is synthesized; Java permits `$` in identifiers, but
            // the JLS reserves it for generated code, so a user identifier
            // spelled `$thrown` is out-of-convention rather than impossible --
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
            // `Body.Opaque` carries modifies as a list of guarded groups; JVerify
            // emits the degenerate form every frontend uses -- one unguarded
            // group holding the user's targets (see `unguardedModifies`). Guards
            // are attached later, inside Strata, by `EliminateExceptions`.
            List<AstNode<StmtExpr>> modifies = new ArrayList<>();
            StmtExpr methodBody = null;

            if (method.body != null) {
                MethodOrLoopContract contract = contractCompiler.getContract(method.body);
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
                    var modExpr = mod.get();
                    // modifies(everything()) is the user's way of writing an
                    // unconstrained frame: map it to Laurel's modifiesWildcard,
                    // which is exactly the fallback we synthesize below when
                    // the user supplies no modifies clause. Doing this up front
                    // avoids sending `everything()` -- a JVerify spec-only stub
                    // -- through convertExpression, which would throw
                    // "Unsupported JVerify method: everything".
                    if (isJVerifyCallNamed(modExpr, "everything")) {
                        // `StmtExpr.All` is the record-AST spelling of the
                        // modifies wildcard.
                        modifies.add(node(new StmtExpr.All(), modExpr));
                    } else {
                        modifies.add(node(convertExpression(modExpr), modExpr));
                    }
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
            boolean canStayTransparent =
                    isPure && postconditions.isEmpty() && modifies.isEmpty();
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
                    ident(methodName), inputs, outputs, preconditions,
                    /*decreases=*/Optional.empty(), body, /*invokeOn=*/Optional.empty(),
                    isInterpretEntry, /*axioms=*/List.of(),
                    // Exceptional contract: the declared type and binding come from
                    // the Java `throws` clause (translated above). `throwsOn`
                    // behaviour cases arrive with the exceptional-contract DSL,
                    // not yet lowered.
                    throwsType, throwsBinding,
                    /*throwsOn=*/List.of());
            procedures.add(proc);
        }

        private StmtExpr emitLoop(StmtExpr cond, List<AstNode<StmtExpr>> invariants, StmtExpr loopBody,
                                  StmtExpr step, List<StmtExpr> preamble,
                                  String breakLbl, String continueLbl) {
            if (breakLbl != null) {
                StmtExpr wrappedBody = new StmtExpr.Block(List.of(node(loopBody)), Optional.of(continueLbl));
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = new StmtExpr.Block(List.of(node(wrappedBody), node(step)), Optional.empty());
                } else {
                    whileBody = wrappedBody;
                }
                StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, Optional.empty(), node(whileBody), false);
                List<AstNode<StmtExpr>> outerStmts = new ArrayList<>();
                for (var p : preamble) outerStmts.add(node(p));
                outerStmts.add(node(whileNode));
                return new StmtExpr.Block(outerStmts, Optional.of(breakLbl));
            } else {
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = new StmtExpr.Block(List.of(node(loopBody), node(step)), Optional.empty());
                } else {
                    whileBody = loopBody;
                }
                StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, Optional.empty(), node(whileBody), false);
                if (preamble.isEmpty()) {
                    return whileNode;
                }
                List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                for (var p : preamble) stmts.add(node(p));
                stmts.add(node(whileNode));
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
                        return emitLoop(cond, parts.invariants, parts.body, null, List.of(),
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
                        return emitLoop(cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhile -> {
                    var breakLbl = freshLabel("loop_break");
                    var continueLbl = freshLabel("loop_continue");
                    var javaLabel = pendingLabel;
                    pendingLabel = null;
                    labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
                    try {
                        var cond = convertExpression(doWhile.cond, renames);
                        var parts = extractLoopParts(doWhile.body, renames);
                        var notCond = primitiveOp(new Operation.Not(), node(cond));
                        var exitIfDone = new StmtExpr.IfThenElse(node(notCond),
                                node(new StmtExpr.Exit(breakLbl)), Optional.empty());
                        var wrappedBody = new StmtExpr.Block(List.of(node(parts.body)), Optional.of(continueLbl));
                        var whileBody = new StmtExpr.Block(List.of(node(wrappedBody), node(exitIfDone)), Optional.empty());
                        var whileNode = new StmtExpr.While(node(new StmtExpr.LiteralBool(true)), parts.invariants, Optional.empty(), node(whileBody), false);
                        yield new StmtExpr.Block(List.of(node(whileNode)), Optional.of(breakLbl));
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
                        labelStack.push(new LabelEntry(pendingLabel, brkLbl, null));
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
                List<AstNode<StmtExpr>> invariants = new ArrayList<>();
                for (var inv : loopContract.loopInvariants()) {
                    invariants.add(node(convertExpression(inv.get(), renames), inv.get()));
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

        private Variable convertVariable(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield new Variable.Local(ident(renames.getOrDefault(name, name)));
                }
                case JCTree.JCFieldAccess fa ->
                    new Variable.Field(node(convertExpression(fa.selected, renames)), ident(fa.name.toString()));
                case JCTree.JCParens parens -> convertVariable(parens.expr, renames);
                default -> throw new JavaViolationException("Unsupported variable expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield var_(renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        new StmtExpr.Assign(List.of(nodeVar(convertVariable(asgn.lhs, renames))),
                                node(convertExpression(asgn.rhs, renames)));
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
                    String calleeName = qualifiedMethodName(methodSym);
                    List<AstNode<StmtExpr>> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(node(convertExpression(arg, renames), arg));
                    }
                    yield new StmtExpr.StaticCall(ident(calleeName), args);
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
                case JCTree.JCNewClass newClass -> {
                    String name = newClass.type.tsym
                            .getQualifiedName().toString()
                            .replace('$', '.');
                    referencedCompositeTypes.add(name);
                    for (var arg : newClass.args) {
                        convertExpression(arg, renames);
                    }
                    yield new StmtExpr.New(ident(name));
                }
                case JCTree.JCInstanceOf instanceOf -> {
                    throw new JavaViolationException(
                        "instanceof on opaque reference types is not yet supported");
                }
                case JCTree.JCTypeCast cast -> convertTypeCast(cast, renames);
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return convertConstantValue(literal.typetag, literal.value);
        }

        /// A Java type cast (explicit or implicit) — `(T) x`. Emitted as a
        /// call to a helper procedure:
        ///
        ///   (T) x    →    $cast_to_T$_from_<S>(x)
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
                throw new JavaViolationException(
                        "Cast to " + cast.type + " is not yet supported");
            }
            String sourceName = laurelTypeName(sourceType);
            String helperName = "$cast_to_" + targetName + "_from_" + sourceName;
            registerCastHelper(helperName, sourceName, sourceType, targetName, targetType);
            // Stamp the operand with its *own* tree, not this cast's: for a
            // chained cast like `(byte) (int) l` the inner cast's diagnostic
            // must point at `(int) l`, not at the whole `(byte) (int) l`.
            return new StmtExpr.StaticCall(ident(helperName),
                    List.of(node(operand, cast.expr)));
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
        ///     opaque(ensures $result == x)
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
        /// `Body.Opaque` marks the procedure as having a specification but no
        /// visible body — the postcondition is what callers see.
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
                    node(var_(LAUREL_RESULT_BINDING)), node(x));
            var postconditions = List.of(
                    new Condition(node(resultEqX), Optional.empty(), new ConditionMode.Both()));
            var proc = new Procedure(
                    ident(helperName),
                    List.of(new Parameter(ident("x"), node(sourceType))),
                    List.of(new Parameter(ident(LAUREL_RESULT_BINDING), node(targetType))),
                    preconditions,
                    /*decreases=*/ Optional.empty(),
                    new Body.Opaque(postconditions, /*implementation=*/ Optional.empty(), unguardedModifies(List.of())),
                    /*invokeOn=*/ Optional.empty(),
                    /*isInterpretEntry=*/ false,
                    /*axioms=*/ List.of(),
                    // Cast helpers are total: they never take the exceptional exit,
                    // so no declared throws type and no `throwsOn` behaviour cases.
                    /*throwsType=*/ Optional.empty(), /*throwsBinding=*/ Optional.empty(),
                    /*throwsOn=*/ List.of());
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

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            AstNode<StmtExpr> inner = node(convertExpression(unary.arg, renames), unary.arg);
            return switch (unary.getTag()) {
                case NOT -> primitiveOp(new Operation.Not(), inner);
                case NEG -> primitiveOp(new Operation.Neg(), inner);
                case PREINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case PREDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                default -> throw new JavaViolationException("Unsupported unary op: " + unary.getTag());
            };
        }

        private StmtExpr convertJVerifyCall(JCTree.JCMethodInvocation invocation,
                                            Symbol.MethodSymbol jverifyMethod,
                                            Map<String, String> renames) {
            var name = jverifyMethod.getQualifiedName().toString();
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
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr qBody = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
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
