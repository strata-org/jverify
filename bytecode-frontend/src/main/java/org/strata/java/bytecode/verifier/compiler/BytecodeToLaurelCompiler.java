package org.strata.java.bytecode.verifier.compiler;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Condition;
import org.strata.jverify.laurel.ConditionMode;
import org.strata.jverify.laurel.ConstrainedType;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Identifier;
import org.strata.jverify.laurel.IncrDecrMode;
import org.strata.jverify.laurel.IncrDecrOp;
import org.strata.jverify.laurel.ModifiesGroup;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.Program;
import org.strata.jverify.laurel.Raw;
import org.strata.jverify.laurel.SourceRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.laurel.Uri;
import org.strata.jverify.laurel.Variable;
import org.strata.java.bytecode.frontend.BytecodeFrontend.DecompiledClass;
import org.strata.java.bytecode.frontend.Decompiler;
import org.strata.jverify.verifier.IntervalTree;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;
import org.strata.jverify.verifier.laurel.FilesMap;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compiles decompiled (JavaParser) ASTs to Laurel. The bytecode-frontend
 * mirror of JFE's {@code JavaToLaurelCompiler} + {@code
 * MethodOrLoopContractCompiler}: same Laurel encodings (binary ops as
 * {@code $op} StaticCalls, constrained-integer prelude, cast helper
 * procedures, loop label scheme), but driven by the Decompiler AST with
 * CharacterRangeTable spans instead of attributed javac trees.
 *
 * <p>Position fidelity: the CRT gives statement-level source ranges. The
 * whole statement's range (minus trailing {@code ;}) stamps the translated
 * statement, matching JFE's {@code sourceTreeFor}; contract clause ranges
 * are narrowed to the spec call's argument text. Sub-expression nodes
 * default to the enclosing statement's range.
 */
public class BytecodeToLaurelCompiler {

    /** Strata's reserved output-parameter name for the short return form. */
    public static final String LAUREL_RESULT_BINDING = "$result";

    private static final FileRange UNKNOWN_RANGE =
            new FileRange(new Uri("<unknown>"), new SourceRange(new Raw(0), new Raw(0)));

    private final Reporter reporter;
    private final boolean verifyByDefault;

    // Per-run state
    private final LinkedHashMap<String, Procedure> castHelpers = new LinkedHashMap<>();
    private final List<Procedure> procedures = new ArrayList<>();
    /** Composite sorts referenced anywhere (JFE's referencedCompositeTypes). */
    private final java.util.LinkedHashSet<String> referencedCompositeTypes = new java.util.LinkedHashSet<>();
    /** Accessed fields per composite (JFE's compositeFields): only accessed fields are declared. */
    private final Map<String, LinkedHashMap<String, org.strata.jverify.laurel.Field>> compositeFields = new LinkedHashMap<>();
    /** Declared field types per program class: fqn -> field name -> (type, isFinal). */
    private final Map<String, Map<String, FieldInfo>> declaredFields = new HashMap<>();
    /** fqn_method -> is annotated @Pure (for the heap-write fallback). */
    private final Map<String, Boolean> pureMethods = new HashMap<>();
    /** fqns of @Contract-annotated classes: consumed as contract definitions, not user code. */
    private final java.util.Set<String> contractClasses = new java.util.HashSet<>();
    /** All type declarations of this compilation by fqn (for contract validation). */
    private final Map<String, TypeDeclaration<?>> sourceTypes = new LinkedHashMap<>();
    /** @Contract declarations in compilation order: (fqn, decl, source uri). */
    private final List<Object[]> contractDecls = new ArrayList<>();
    /** javac classpath entries, for inventorying library contractees' methods. */
    private final List<java.nio.file.Path> classPath;

    private record FieldInfo(Type type, boolean isFinal) {
    }
    private final Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses = new HashMap<>();
    private final Map<URI, LineIndex> lineIndexes = new HashMap<>();
    /** simple or fqn class name -> fqn ('$'->'.'), for resolving static call owners. */
    private final Map<String, String> knownClasses = new HashMap<>();
    /** fqn -> direct superclass fqn, from the classfiles of this compilation. */
    private final Map<String, String> programSuper = new HashMap<>();
    /** Composite sort -> its extended (parent) sort (JFE's compositeExtends). */
    private final Map<String, String> compositeExtends = new LinkedHashMap<>();
    /** Instance procedures per composite sort (JFE's instanceProcsBySort). */
    private final Map<String, List<Procedure>> instanceProcsBySort = new LinkedHashMap<>();
    /** Classfile models of this compilation by fqn ('$'->'.'), for the dispatch/overload gates. */
    private final Map<String, java.lang.classfile.ClassModel> programClassModels = new HashMap<>();

    /**
     * Synthesized receiver parameter on instance procedures (JFE's
     * LAUREL_SELF_BINDING). '?' is outside Java's identifier grammar but
     * inside Strata's, so no user local can collide; Strata's
     * LiftInstanceProcedures prepends the receiver argument, so the lifted
     * procedure must declare it first.
     */
    static final String LAUREL_SELF_BINDING = "self?";

    /**
     * Everything the refusal-propagation fixpoint needs about a translated or
     * refused method (JFE keys this on MethodSymbols; here the key is
     * "classFqn#name", unique because overloads are refused).
     */
    private static final class MethodInfo {
        final String classFqn;
        final String name;
        final boolean isStatic;
        final URI uri;
        final Range namePoint;
        /** Distinguishes same-name overload declarations (JFE keys on MethodSymbol identity). */
        final int ordinal;
        /** Opted out of verification: refusals demote silently, never diagnose. */
        final boolean quiet;
        MethodVerificationStatus status;
        Procedure proc;         // emitted procedure, for the drop pass
        String sort;            // composite sort holding proc (null for statics)
        boolean refused;        // translation threw, or demoted by the fixpoint
        final java.util.Set<String> callees = new java.util.LinkedHashSet<>();

        MethodInfo(String classFqn, String name, boolean isStatic, URI uri, Range namePoint,
                   int ordinal, boolean quiet) {
            this.classFqn = classFqn;
            this.name = name;
            this.isStatic = isStatic;
            this.uri = uri;
            this.namePoint = namePoint;
            this.ordinal = ordinal;
            this.quiet = quiet;
        }

        /** Per-declaration identity (dedup key). */
        String key() {
            return classFqn + "#" + name + "@" + ordinal;
        }

        /** By-name identity: what a call edge can actually reference. */
        String nameKey() {
            return classFqn + "#" + name;
        }
    }

    private final Map<String, MethodInfo> methodInfos = new LinkedHashMap<>();
    /** The method being translated (callee edges are recorded on it). */
    private MethodInfo currentMethodInfo;
    /** Fqn of the class whose method is being translated: the type of `this`. */
    private String currentClassFqn;
    /** The current method's class-typed return sort, or null (for opaque lambda returns). */
    private String currentReturnSort;
    /** Converting a contract argument (precondition/postcondition/invariant/modifies). */
    private boolean inContractContext;
    /** Type-parameter names in scope (method + enclosing class): refuse as JFE's Unsupported type: T. */
    private java.util.Set<String> currentTypeParams = java.util.Set.of();
    /** Diagnostic dedup (JFE's reportedInstanceRefusals): methodKey -> reasons reported. */
    private final Map<String, java.util.Set<String>> reportedRefusals = new HashMap<>();

    // Per-file state
    private URI currentUri;
    private String currentSource;
    private LineIndex currentIndex;
    /** Default range for nodes without their own position: the enclosing statement's. */
    private FileRange currentStatementRange = UNKNOWN_RANGE;
    /** Local-name renames in effect (postcondition lambda param -> $result). */
    private Map<String, String> renames = Map.of();
    /** Per-file single-type imports of the decompiled unit: simple name -> fqn. */
    private Map<String, String> currentImports = Map.of();

    // Per-method state (exceptions)
    /**
     * The declared exceptional join class (fqn) of the method being
     * translated, from its {@code throws} clause; null when it has none.
     * JFE's currentThrowsJoin.
     */
    private String currentThrowsJoin;
    /**
     * Catch types (fqns) of enclosing {@code try} BODIES, innermost first. A
     * throw in a catch arm or finalizer answers to the next try out, so the
     * push is scoped to the body only (JFE's enclosingCatchTypes).
     */
    private final Deque<List<String>> enclosingCatchTypes = new ArrayDeque<>();
    /** Synthesized name binding the thrown value in a `throws (e: T)` clause (JFE's THROWN_BINDING). */
    private static final String THROWN_BINDING = "$thrown";

    private int labelCounter = 0;

    public BytecodeToLaurelCompiler(Reporter reporter) {
        this(reporter, true, List.of());
    }

    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault) {
        this(reporter, verifyByDefault, List.of());
    }

    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault,
                                    List<java.nio.file.Path> classPath) {
        this.reporter = reporter;
        this.verifyByDefault = verifyByDefault;
        this.classPath = classPath;
        shouldVerifies.push(verifyByDefault ? ShouldVerifyMode.DefaultYes : ShouldVerifyMode.DefaultNo);
    }

    public record AnalysisResult(Program program,
                                 FilesMap filesMap,
                                 Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses) {
    }

    /**
     * @param sources source text by URI (used for spans -> byte offsets and
     *        for narrowing contract ranges; must be the exact text javac saw)
     * @param classes decompiled top-level classes; each is matched to its
     *        source via the SourceFile attribute's file name
     */
    public AnalysisResult analyze(Map<URI, String> sources, List<DecompiledClass> classes) {
        for (var entry : sources.entrySet()) {
            lineIndexes.put(entry.getKey(), new LineIndex(entry.getValue()));
            sourceTexts.put(entry.getKey(), entry.getValue());
        }
        for (var cls : classes) {
            String fqn = cls.internalName().replace('/', '.').replace('$', '.');
            knownClasses.put(fqn, fqn);
            knownClasses.put(simpleName(fqn), fqn);
            programClassModels.put(fqn, cls.classModel());
            for (var nested : cls.nestedModels()) {
                programClassModels.put(
                        nested.thisClass().asInternalName().replace('/', '.').replace('$', '.'), nested);
            }
            // Superclass edges straight from the classfiles: authoritative for
            // the exception-hierarchy walks (isSubclassOf / LCA / extends links).
            cls.classModel().superclass().ifPresent(sup ->
                    programSuper.put(fqn, sup.asInternalName().replace('/', '.').replace('$', '.')));
            for (var nested : cls.nestedModels()) {
                String nestedFqn = nested.thisClass().asInternalName().replace('/', '.').replace('$', '.');
                nested.superclass().ifPresent(sup ->
                        programSuper.put(nestedFqn, sup.asInternalName().replace('/', '.').replace('$', '.')));
            }
            // Nested classes resolve by simple name and by Outer.Inner form,
            // so `Point p` and `new Point()` in decompiled code qualify to
            // the same composite sort as the field declarations.
            for (var nested : cls.nestedModels()) {
                String nestedFqn = nested.thisClass().asInternalName().replace('/', '.').replace('$', '.');
                knownClasses.put(nestedFqn, nestedFqn);
                knownClasses.put(simpleName(nestedFqn), nestedFqn);
                String outerSimple = simpleName(fqn);
                if (nestedFqn.startsWith(fqn + ".")) {
                    knownClasses.put(outerSimple + nestedFqn.substring(fqn.length()), nestedFqn);
                }
            }
        }

        // Pre-pass: collect declared fields and @Pure markers of every class
        // in the compilation, so field accesses and the heap-write fallback
        // can resolve them regardless of unit order.
        for (var cls : classes) {
            String fqn = cls.internalName().replace('/', '.').replace('$', '.');
            URI uri = sourceUriFor(cls, sources.keySet());
            for (TypeDeclaration<?> decl : cls.unit().getTypes()) {
                collectDeclarations(fqn, decl, uri);
            }
        }

        validateContracts();

        for (var cls : classes) {
            currentUri = sourceUriFor(cls, sources.keySet());
            currentSource = currentUri != null ? sources.get(currentUri) : null;
            currentIndex = currentUri != null ? lineIndexes.get(currentUri) : null;
            compileUnit(cls);
        }

        // Refusal propagation (JFE's demoteCallersOfRefusedMethods): a method
        // that calls a refused one is itself demoted -- soundness, since its
        // callee's contract was never established. Iterated to a fixpoint so
        // A -> B -> C chains demote fully, then the demoted methods' emitted
        // procedures are dropped (an InstanceCall/StaticCall to an unemitted
        // procedure would die in Strata resolution).
        demoteCallersOfRefusedMethods();
        dropRefusedProcedures();

        // Composite declarations: all referenced class-typed sorts, each with
        // only its accessed fields (JFE's scheme), after the prelude types.
        // Ancestors first: Laurel builds a composite's scope by copying each
        // parent's, so a parent must be declared before its children.
        var orderedComposites = new java.util.LinkedHashSet<String>();
        for (var typeName : referencedCompositeTypes) {
            var chain = new ArrayDeque<String>();
            for (String c = typeName; c != null && referencedCompositeTypes.contains(c);
                    c = compositeExtends.get(c)) {
                chain.push(c);
            }
            orderedComposites.addAll(chain);
        }
        List<TypeDefinition> types = new ArrayList<>(getPredefinedTypes());
        for (var typeName : orderedComposites) {
            List<org.strata.jverify.laurel.Field> fields = compositeFields.containsKey(typeName)
                    ? new ArrayList<>(compositeFields.get(typeName).values())
                    : List.of();
            // Extends edge (exception hierarchies): recorded by linkExtends;
            // the JCL ancestors this declares (Exception, Throwable, ...) are
            // empty, uninterpreted sorts (JFE's scheme).
            List<AstNode<HighType>> extending = compositeExtends.containsKey(typeName)
                    ? List.of(node(new HighType.UserDefined(ident(compositeExtends.get(typeName)))))
                    : List.of();
            types.add(new TypeDefinition.Composite(new org.strata.jverify.laurel.CompositeType(
                    ident(typeName), /*typeArgs=*/List.of(), extending, fields,
                    instanceProcsBySort.getOrDefault(typeName, List.of()))));
        }
        List<Procedure> allProcedures = new ArrayList<>(castHelpers.values());
        allProcedures.addAll(procedures);
        var program = new Program(allProcedures, List.of(), types, List.of());

        FilesMap filesMap = (uri, offset) -> {
            var index = lineIndexes.get(uri);
            if (index == null) {
                throw new RuntimeException("Could not find line map for " + uri);
            }
            return index.position(offset);
        };
        return new AnalysisResult(program, filesMap, methodStatuses);
    }

    /**
     * Port of JFE's demoteCallersOfRefusedMethods: one round marks every
     * method that calls an already-refused one, reporting {@code call to
     * 'X', which could not be translated} (severity by the CALLER's own
     * staticness, anchored at the caller's declaration name, deduped per
     * (caller, reason) -- the demotion itself is never deduped). Iterated to
     * a fixpoint: A -> B -> C with C refused demotes B in round one and A in
     * round two. Terminates because the refused set only grows and is
     * bounded by the method count.
     */
    private void demoteCallersOfRefusedMethods() {
        boolean changed = true;
        while (changed) {
            changed = false;
            // A call edge is by name (that is all a Laurel call carries), so
            // a callee name is refused when ANY declaration of it is. Quiet
            // (opted-out) refusals are excluded: calls to opted-out methods
            // are refused at the call site by the arity-aware check, and an
            // opted-out overload must not poison calls to its emitted
            // sibling (JFE keys demotion on symbols, not names).
            var refusedNames = new java.util.HashSet<String>();
            for (var info : methodInfos.values()) {
                if (info.refused && !info.quiet) {
                    refusedNames.add(info.nameKey());
                }
            }
            for (var info : methodInfos.values()) {
                if (info.refused) {
                    continue;
                }
                for (String calleeKey : info.callees) {
                    if (!refusedNames.contains(calleeKey)) {
                        continue;
                    }
                    info.refused = true;
                    if (info.status != null) {
                        info.status.setVerificationStatus(
                                MethodVerificationStatus.VerificationStatus.Skipped);
                    }
                    reportRefusal(info, "call to '"
                            + calleeKey.substring(calleeKey.indexOf('#') + 1)
                            + "', which could not be translated");
                    changed = true;
                    break;
                }
            }
        }
    }

    /**
     * Drop the emitted procedures of demoted methods (JFE's post-fixpoint
     * drop pass): their bodies reference unemitted callees, and Strata would
     * fail resolution on them.
     */
    private void dropRefusedProcedures() {
        for (var info : methodInfos.values()) {
            if (!info.refused || info.proc == null) {
                continue;
            }
            if (info.sort == null) {
                procedures.remove(info.proc);
            } else {
                var list = instanceProcsBySort.get(info.sort);
                if (list != null) {
                    list.remove(info.proc);
                }
            }
            info.proc = null;
        }
    }

    private void collectDeclarations(String fqn, TypeDeclaration<?> decl, URI uri) {
        sourceTypes.put(fqn, decl);
        if (decl.getAnnotationByName("Contract").isPresent()) {
            // A @Contract class defines specs for another type; JFE's
            // ExternalContractCompiler consumes it before method counting, so
            // its members never appear as user methods.
            contractClasses.add(fqn);
            contractDecls.add(new Object[]{fqn, decl, uri});
        }
        var fields = declaredFields.computeIfAbsent(fqn, k -> new LinkedHashMap<>());
        // Same disambiguation as compileType's walk, so sourceTypes keys agree.
        var nestedNameCounts = new HashMap<String, Integer>();
        for (var member : decl.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.FieldDeclaration fieldDecl) {
                for (VariableDeclarator v : fieldDecl.getVariables()) {
                    fields.put(v.getNameAsString(), new FieldInfo(v.getType(), fieldDecl.isFinal()));
                }
            }
            if (member instanceof MethodDeclaration m) {
                pureMethods.put(fqn + "_" + m.getNameAsString(),
                        m.getAnnotationByName("Pure").isPresent());
            }
            if (member instanceof TypeDeclaration<?> nested) {
                collectDeclarations(nestedFqn(fqn, nested, nestedNameCounts), nested, uri);
            }
        }
        // Record components are fields too.
        if (decl instanceof com.github.javaparser.ast.body.RecordDeclaration record) {
            for (Parameter p : record.getParameters()) {
                fields.put(p.getNameAsString(), new FieldInfo(p.getType(), true));
            }
        }
    }

    // --- External @Contract validation (port of ExternalContractCompiler's checks) ---

    /**
     * Validate every @Contract class: target resolution, one-contract-per-
     * target, no contracts for concrete source classes, no stray annotations,
     * matching type parameters, and method signatures matching the contractee
     * (including inherited methods; classpath contractees are inventoried
     * from their classfiles). Messages and 1-char positions mirror JFE's.
     */
    private void validateContracts() {
        // Decompiler may reorder nested classes relative to the source;
        // duplicate-contract errors must fire on the LATER declaration in
        // SOURCE order (JFE behavior), so sort by source position first.
        contractDecls.sort(java.util.Comparator
                .comparing((Object[] e) -> String.valueOf(e[2]))
                .thenComparingInt(e -> {
                    String src = sourceTexts.get((URI) e[2]);
                    if (src == null) {
                        return Integer.MAX_VALUE;
                    }
                    var m = java.util.regex.Pattern
                            .compile("\\b(?:class|interface|record)\\s+"
                                    + java.util.regex.Pattern.quote(((TypeDeclaration<?>) e[1]).getNameAsString()) + "\\b")
                            .matcher(src);
                    return m.find() ? m.start() : Integer.MAX_VALUE;
                }));
        var seenTargets = new HashMap<String, String>();
        for (var entry : contractDecls) {
            String fqn = (String) entry[0];
            TypeDeclaration<?> decl = (TypeDeclaration<?>) entry[1];
            URI uri = (URI) entry[2];
            currentUri = uri;
            currentSource = uri != null && lineIndexes.containsKey(uri) ? sourceOf(uri) : null;
            currentIndex = uri != null ? lineIndexes.get(uri) : null;

            String explicitTarget = explicitContractTarget(decl);
            String targetFqn = explicitTarget;
            if (targetFqn == null && decl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid) {
                var supers = new ArrayList<com.github.javaparser.ast.type.ClassOrInterfaceType>();
                supers.addAll(cid.getExtendedTypes());
                supers.addAll(cid.getImplementedTypes());
                if (!supers.isEmpty()) {
                    targetFqn = resolveTypeName(supers.get(0).getNameWithScope(), decl);
                }
            }
            if (targetFqn == null) {
                reporter.reportError(uri, classKeywordPoint(decl),
                        "could not find a target for @Contract class '" + decl.getNameAsString() + "'");
                continue;
            }
            String targetSimple = simpleName(targetFqn);

            String previous = seenTargets.putIfAbsent(targetFqn, fqn);
            if (previous != null) {
                reporter.reportError(uri, annotationPoint(decl, "Contract"),
                        "class '" + targetSimple + "' must not be referenced by more than one @Contract annotation");
            }

            var targetDecl = sourceTypes.get(targetFqn);
            if (targetDecl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration targetCid
                    && !targetCid.isInterface() && !targetCid.isAbstract()) {
                reporter.reportError(uri, annotationPoint(decl, "Contract"),
                        "class '" + targetSimple + "' must not have an externally defined contract"
                                + " because all its contracts can be defined internally");
            }

            for (var annotation : decl.getAnnotations()) {
                if (!annotation.getNameAsString().equals("Contract")) {
                    reporter.reportError(uri, annotationPoint(decl, annotation.getNameAsString()),
                            "annotation '" + annotation.getNameAsString() + "' on @Contract class '"
                                    + decl.getNameAsString() + "' is not allowed, because it must be placed on the contractee");
                }
            }

            if (targetDecl != null && decl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration contractCid
                    && targetDecl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration targetCid) {
                var contractParams = contractCid.getTypeParameters().stream()
                        .map(p -> p.getNameAsString()).toList();
                var targetParams = targetCid.getTypeParameters().stream()
                        .map(p -> p.getNameAsString()).toList();
                if (!contractParams.equals(targetParams)) {
                    reporter.reportError(uri, classKeywordPoint(decl),
                            "Contract class '" + decl.getNameAsString() + "' has different type parameters than the contractee '"
                                    + targetSimple + "'. The parameters must have the same names");
                }
            }

            validateContractMethods(decl, targetFqn, uri);
        }
        currentUri = null;
        currentSource = null;
        currentIndex = null;
    }

    /** name+arity signatures of the contractee's methods (with supers), or null when unknowable. */
    private java.util.Set<String> contracteeMethodSignatures(String targetFqn) {
        var out = new java.util.HashSet<String>();
        String current = targetFqn;
        while (current != null && !current.equals("java.lang.Object")) {
            var srcDecl = sourceTypes.get(current);
            if (srcDecl != null) {
                for (var m : srcDecl.getMembers()) {
                    if (m instanceof MethodDeclaration md) {
                        out.add(md.getNameAsString() + "/" + md.getParameters().size());
                    }
                }
                if (srcDecl instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid) {
                    var supers = new ArrayList<com.github.javaparser.ast.type.ClassOrInterfaceType>();
                    supers.addAll(cid.getExtendedTypes());
                    supers.addAll(cid.getImplementedTypes());
                    // Only the first super is walked linearly; interface method
                    // sets are merged for source types below.
                    for (int i = 1; i < supers.size(); i++) {
                        var extra = contracteeMethodSignatures(resolveTypeName(supers.get(i).getNameWithScope(), srcDecl));
                        if (extra != null) out.addAll(extra);
                    }
                    current = supers.isEmpty() ? null
                            : resolveTypeName(supers.get(0).getNameWithScope(), srcDecl);
                } else {
                    current = null;
                }
                continue;
            }
            var cm = loadClassModel(current);
            if (cm == null) {
                return null; // unknowable contractee (e.g. a JDK type): skip checks
            }
            for (var m : cm.methods()) {
                String name = m.methodName().stringValue();
                if (name.equals("<init>") || name.equals("<clinit>")) continue;
                out.add(name + "/" + m.methodTypeSymbol().parameterCount());
            }
            current = cm.superclass()
                    .map(sc -> sc.asInternalName().replace('/', '.').replace('$', '.'))
                    .orElse(null);
        }
        return out;
    }

    private void validateContractMethods(TypeDeclaration<?> decl, String targetFqn, URI uri) {
        var signatures = contracteeMethodSignatures(targetFqn);
        if (signatures == null) {
            return;
        }
        for (var member : decl.getMembers()) {
            if (!(member instanceof MethodDeclaration method)) {
                continue;
            }
            String key = method.getNameAsString() + "/" + method.getParameters().size();
            if (!signatures.contains(key)) {
                reporter.reportError(uri, contractMethodNamePoint(method),
                        "method '" + method.getNameAsString() + "' is part of a @Contract class,"
                                + " but its signature does not match any method from the contractee");
                continue;
            }
            // Redeclared contract: the matched contractee method (defined in
            // this compilation) already carries its own contract header.
            var targetDecl = sourceTypes.get(targetFqn);
            if (targetDecl != null) {
                for (var m : targetDecl.getMembers()) {
                    if (m instanceof MethodDeclaration targetMethod
                            && targetMethod.getNameAsString().equals(method.getNameAsString())
                            && targetMethod.getParameters().size() == method.getParameters().size()
                            && hasContractHeader(targetMethod)
                            && hasContractHeader(method)) {
                        reporter.reportError(uri, contractMethodNamePoint(method),
                                "method '" + method.getNameAsString() + "' already has an internally defined contract");
                    }
                }
            }
        }
    }

    private boolean hasContractHeader(MethodDeclaration method) {
        var body = method.getBody().orElse(null);
        if (body == null) {
            return false;
        }
        var contract = extractContract(body.getStatements());
        return !contract.preconditions().isEmpty() || !contract.postconditions().isEmpty()
                || !contract.reads().isEmpty() || !contract.modifies().isEmpty();
    }

    /** The @Contract annotation's explicit target fqn (@Contract(Foo.class)), or null. */
    private String explicitContractTarget(TypeDeclaration<?> decl) {
        var annotation = decl.getAnnotationByName("Contract").orElse(null);
        Expression value = null;
        if (annotation instanceof com.github.javaparser.ast.expr.SingleMemberAnnotationExpr single) {
            value = single.getMemberValue();
        } else if (annotation instanceof com.github.javaparser.ast.expr.NormalAnnotationExpr normal) {
            value = normal.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .map(com.github.javaparser.ast.expr.MemberValuePair::getValue)
                    .findFirst().orElse(null);
        }
        if (value instanceof com.github.javaparser.ast.expr.ClassExpr classExpr) {
            return resolveTypeName(classExpr.getType().asString(), decl);
        }
        return null;
    }

    /** Resolve a decompiled type name to an fqn: compilation classes, then the unit's imports. */
    private String resolveTypeName(String name, com.github.javaparser.ast.Node context) {
        String known = knownClasses.get(name);
        if (known != null) {
            return known;
        }
        String simple = simpleName(name.replace('.', '.'));
        var unit = context.findCompilationUnit().orElse(null);
        if (unit != null) {
            for (var imp : unit.getImports()) {
                String imported = imp.getNameAsString();
                if (!imp.isAsterisk() && imported.endsWith("." + simple)) {
                    return imported;
                }
            }
        }
        return name;
    }

    /** Load a classfile from the javac classpath (dirs and jars), or null. */
    private java.lang.classfile.ClassModel loadClassModel(String fqn) {
        var direct = loadClassModelExact(fqn);
        if (direct != null) {
            return direct;
        }
        // Nested classfile: logical Outer.Inner is Outer$Inner on disk. Try
        // '$' at each dot position from the right.
        int dot = fqn.lastIndexOf('.');
        while (dot > 0) {
            String binary = fqn.substring(0, dot) + "$" + fqn.substring(dot + 1).replace('.', '$');
            var cm = loadClassModelExact(binary);
            if (cm != null) {
                return cm;
            }
            dot = fqn.lastIndexOf('.', dot - 1);
        }
        return null;
    }

    private java.lang.classfile.ClassModel loadClassModelExact(String binaryFqn) {
        String rel = binaryFqn.replace('.', '/') + ".class";
        for (var entry : classPath) {
            try {
                if (java.nio.file.Files.isDirectory(entry)) {
                    var p = entry.resolve(rel);
                    if (java.nio.file.Files.exists(p)) {
                        return java.lang.classfile.ClassFile.of().parse(p);
                    }
                } else if (entry.toString().endsWith(".jar") && java.nio.file.Files.exists(entry)) {
                    try (var jar = new java.util.jar.JarFile(entry.toFile())) {
                        var je = jar.getJarEntry(rel);
                        if (je != null) {
                            return java.lang.classfile.ClassFile.of().parse(jar.getInputStream(je).readAllBytes());
                        }
                    }
                }
            } catch (java.io.IOException | RuntimeException ignore) {
                // fall through to the next entry
            }
        }
        return null;
    }

    /** True when the character at {@code offset} sits after a {@code //} on its line. */
    private boolean inLineComment(int offset) {
        int lineStart = currentSource.lastIndexOf('\n', offset - 1) + 1;
        int comment = currentSource.indexOf("//", lineStart);
        return comment >= 0 && comment < offset;
    }

    /** 1-char range at the contract class's 'class'/'interface' keyword (JFE's JCClassDecl position). */
    private Range classKeywordPoint(TypeDeclaration<?> decl) {
        if (currentSource == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b(?:class|interface|record)\\s+" + java.util.regex.Pattern.quote(decl.getNameAsString()) + "\\b")
                .matcher(currentSource);
        while (matcher.find()) {
            if (!inLineComment(matcher.start())) {
                var start = currentIndex.position(matcher.start());
                return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
            }
        }
        return Reporter.NO_RANGE;
    }

    /** 1-char range at an annotation's '@' on the contract class (last occurrence before the class keyword). */
    private Range annotationPoint(TypeDeclaration<?> decl, String annotationName) {
        if (currentSource == null) {
            return Reporter.NO_RANGE;
        }
        var kw = java.util.regex.Pattern
                .compile("\\b(?:class|interface|record)\\s+" + java.util.regex.Pattern.quote(decl.getNameAsString()) + "\\b")
                .matcher(currentSource);
        int limit = kw.find() ? kw.start() : currentSource.length();
        var matcher = java.util.regex.Pattern
                .compile("@" + java.util.regex.Pattern.quote(annotationName) + "\\b")
                .matcher(currentSource);
        int pos = -1;
        while (matcher.find() && matcher.start() < limit) {
            if (!inLineComment(matcher.start())) {
                pos = matcher.start();
            }
        }
        if (pos < 0) {
            return classKeywordPoint(decl);
        }
        var start = currentIndex.position(pos);
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    /** 1-char range at a contract method's name (span-anchored when possible, else first match). */
    private Range contractMethodNamePoint(MethodDeclaration method) {
        var anchored = namePointRange(method);
        if (anchored != Reporter.NO_RANGE) {
            return anchored;
        }
        if (currentSource == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(method.getNameAsString()) + "\\s*\\(")
                .matcher(currentSource);
        if (!matcher.find()) {
            return Reporter.NO_RANGE;
        }
        var start = currentIndex.position(matcher.start());
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    private final Map<URI, String> sourceTexts = new HashMap<>();

    private String sourceOf(URI uri) {
        return sourceTexts.get(uri);
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    /** Match a decompiled class to its source file by the SourceFile attribute name. */
    private static URI sourceUriFor(DecompiledClass cls, Iterable<URI> sourceUris) {
        if (cls.sourceFileName() == null) {
            return null;
        }
        for (URI uri : sourceUris) {
            String path = uri.getPath();
            if (path != null && path.endsWith("/" + cls.sourceFileName())) {
                return uri;
            }
        }
        return null;
    }

    // --- Node helpers (mirror JFE's) ---

    /**
     * Wrap modifies {@code targets} into the degenerate group form every
     * frontend emits: one unguarded {@link ModifiesGroup} that is always
     * present, even when the target list is empty (an empty group is the
     * opaque default "nothing changes" frame -- dropping it would leave the
     * procedure unframed). Mirrors JFE's JavaToLaurelCompiler.unguardedModifies.
     */
    private static List<ModifiesGroup> unguardedModifies(List<AstNode<StmtExpr>> targets) {
        return List.of(new ModifiesGroup(targets, Optional.empty(), Optional.empty()));
    }

    private static Identifier ident(String name) {
        return new Identifier(name, Optional.empty(), UNKNOWN_RANGE);
    }

    private AstNode<StmtExpr> node(StmtExpr expr) {
        return new AstNode<>(expr, currentStatementRange);
    }

    private AstNode<StmtExpr> node(StmtExpr expr, FileRange range) {
        return new AstNode<>(expr, range);
    }

    private AstNode<Variable> nodeVar(Variable v) {
        return new AstNode<>(v, currentStatementRange);
    }

    private static AstNode<HighType> node(HighType type) {
        return new AstNode<>(type, UNKNOWN_RANGE);
    }

    private static StmtExpr var_(String name) {
        return new StmtExpr.Var(new Variable.Local(ident(name)));
    }

    /** Java mirror of Strata's {@code Operation.procName}; see JFE for the sync warning. */
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

    @SafeVarargs
    private StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return new StmtExpr.StaticCall(ident(operationProcName(op)), List.of(args));
    }

    // --- Predefined constrained integer types (same BOUNDS as JFE) ---

    private static final LinkedHashMap<String, Long[]> BOUNDS = new LinkedHashMap<>();

    static {
        BOUNDS.put("int8", new Long[]{-128L, 127L});
        BOUNDS.put("int16", new Long[]{-32768L, 32767L});
        BOUNDS.put("int32", new Long[]{-2147483648L, 2147483647L});
        BOUNDS.put("int64", new Long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        BOUNDS.put("char", new Long[]{0L, 65535L});
        BOUNDS.put("nat7", new Long[]{0L, 127L});
        BOUNDS.put("nat15", new Long[]{0L, 32767L});
        BOUNDS.put("nat31", new Long[]{0L, 2147483647L});
        BOUNDS.put("nat63", new Long[]{0L, Long.MAX_VALUE});
        BOUNDS.put("nat", new Long[]{0L, null});
    }

    private List<TypeDefinition> getPredefinedTypes() {
        var types = new ArrayList<TypeDefinition>();
        for (var entry : BOUNDS.entrySet()) {
            types.add(makeConstrainedType(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return types;
    }

    private TypeDefinition makeConstrainedType(String name, Long min, Long max) {
        var x = var_("x");
        var bounds = new ArrayList<StmtExpr>();
        if (min != null) {
            bounds.add(primitiveOp(new Operation.Geq(), noRange(x), noRange(longLiteral(min))));
        }
        if (max != null) {
            bounds.add(primitiveOp(new Operation.Leq(), noRange(x), noRange(longLiteral(max))));
        }
        var constraint = bounds.stream()
                .reduce((a, b) -> primitiveOp(new Operation.And(), noRange(a), noRange(b)))
                .orElse(new StmtExpr.LiteralBool(true));
        var witness = new StmtExpr.LiteralInt(0);
        return new TypeDefinition.Constrained(new ConstrainedType(
                ident(name), node(new HighType.TInt()), ident("x"), noRange(constraint), noRange(witness)));
    }

    private static AstNode<StmtExpr> noRange(StmtExpr e) {
        return new AstNode<>(e, UNKNOWN_RANGE);
    }

    private StmtExpr longLiteral(long val) {
        if (val >= 0 || val == Long.MIN_VALUE) {
            return new StmtExpr.LiteralInt(val);
        }
        return primitiveOp(new Operation.Neg(), noRange(new StmtExpr.LiteralInt(-val)));
    }

    // --- Ranges from CRT spans ---

    private FileRange rangeOf(int startOffset, int endOffset) {
        return new FileRange(new Uri(currentUri.toString()),
                new SourceRange(new Raw(startOffset), new Raw(endOffset)));
    }

    /**
     * The statement's source range from its CRT span, with the trailing
     * {@code ;} (and whitespace) stripped — JFE stamps the expression, not
     * the ExpressionStatement, so its ranges exclude the semicolon
     * ({@code sourceTreeFor}). Loop-body statements are the one exception:
     * JFE stamps those with the statement itself (semicolon included), see
     * {@link #statementRangeFull}.
     */
    private FileRange statementRange(Statement s) {
        if (!s.containsData(Decompiler.SPAN) || currentIndex == null) {
            return currentStatementRange;
        }
        var span = s.getData(Decompiler.SPAN);
        int start = currentIndex.offset(span.beginLine(), span.beginCol());
        int end = currentIndex.offset(span.endLine(), span.endCol());
        // A statement that materializes a lambda carries the invokedynamic's
        // CRT window, which starts at the class declaration rather than the
        // statement. Reconstruct a single-line range from the (correct) end
        // line: first non-whitespace column .. span end.
        if (span.beginLine() != span.endLine()
                && s.findFirst(com.github.javaparser.ast.expr.LambdaExpr.class).isPresent()) {
            int lineStart = currentIndex.offset(span.endLine(), 1);
            int firstNonWs = lineStart;
            while (firstNonWs < end && Character.isWhitespace(currentSource.charAt(firstNonWs))) {
                firstNonWs++;
            }
            start = firstNonWs;
        }
        // JFE's sourceTreeFor only narrows JCExpressionStatements to their
        // expression; a local declaration is a JCVariableDecl and keeps its
        // full statement range including the semicolon. Our decompiled AST
        // wraps declarations in ExpressionStmt(VariableDeclarationExpr), so
        // exclude that shape from the narrowing.
        if (s instanceof ExpressionStmt es
                && !(es.getExpression() instanceof VariableDeclarationExpr)) {
            while (end > start && (currentSource.charAt(end - 1) == ';'
                    || Character.isWhitespace(currentSource.charAt(end - 1)))) {
                end--;
            }
        }
        return rangeOf(start, end);
    }

    /** The raw CRT statement range, semicolon included (JFE's loop-body stamping). */
    private FileRange statementRangeFull(Statement s) {
        if (!s.containsData(Decompiler.SPAN) || currentIndex == null) {
            return currentStatementRange;
        }
        var span = s.getData(Decompiler.SPAN);
        return rangeOf(currentIndex.offset(span.beginLine(), span.beginCol()),
                currentIndex.offset(span.endLine(), span.endCol()));
    }

    /**
     * Narrow a spec-call statement's range to its argument text:
     * {@code precondition(lo <= hi);} -> the {@code lo <= hi} range.
     * Falls back to the whole statement range when the shape is unexpected.
     */
    private FileRange argumentRange(FileRange callRange) {
        if (currentSource == null || callRange == UNKNOWN_RANGE) {
            return callRange;
        }
        int start = (int) callRange.range().start().byteIdx();
        int end = (int) callRange.range().stop().byteIdx();
        int open = currentSource.indexOf('(', start);
        if (open < 0 || open >= end) {
            return callRange;
        }
        int depth = 0;
        int close = -1;
        for (int i = open; i < end; i++) {
            char c = currentSource.charAt(i);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) {
                close = i;
                break;
            }
        }
        if (close < 0) {
            return callRange;
        }
        int argStart = open + 1;
        int argEnd = close;
        while (argStart < argEnd && Character.isWhitespace(currentSource.charAt(argStart))) argStart++;
        while (argEnd > argStart && Character.isWhitespace(currentSource.charAt(argEnd - 1))) argEnd--;
        return rangeOf(argStart, argEnd);
    }

    /**
     * The source range of a lambda's body inside a spec-call statement:
     * everything after the first {@code ->} up to (excluding) the spec call's
     * closing parenthesis. {@code postcondition((int res) -> res > 0)} ->
     * the {@code res > 0} range. Single-line spec calls only (the CRT gives
     * no sub-statement structure to do better with).
     */
    private FileRange lambdaBodyRange(FileRange callRange) {
        if (currentSource == null || callRange == UNKNOWN_RANGE) {
            return callRange;
        }
        int start = (int) callRange.range().start().byteIdx();
        int end = (int) callRange.range().stop().byteIdx();
        int arrow = currentSource.indexOf("->", start);
        if (arrow < 0 || arrow >= end) {
            return argumentRange(callRange);
        }
        int bodyStart = arrow + 2;
        while (bodyStart < end && Character.isWhitespace(currentSource.charAt(bodyStart))) {
            bodyStart++;
        }
        // callRange ends at the spec call's ')' (semicolon already stripped).
        int bodyEnd = end - 1;
        while (bodyEnd > bodyStart && Character.isWhitespace(currentSource.charAt(bodyEnd - 1))) {
            bodyEnd--;
        }
        return rangeOf(bodyStart, bodyEnd);
    }

    private Range positionRange(FileRange range) {
        if (currentIndex == null || range == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        return new Range(currentIndex.position((int) range.range().start().byteIdx()),
                currentIndex.position((int) range.range().stop().byteIdx()));
    }

    // --- Contract extraction (mirror of MethodOrLoopContractCompiler) ---

    /** Spec-method names that form a method/loop contract header. */
    private static boolean isHeaderContractName(String name) {
        return switch (name) {
            case "precondition", "postcondition", "invariant", "decreases", "reads", "modifies" -> true;
            default -> false;
        };
    }

    /** The JVerify spec-method name of a call, or null if it isn't one. */
    private static String jverifyCallName(Expression expr) {
        if (!(expr instanceof MethodCallExpr call)) {
            return null;
        }
        // The decompiler emits spec calls owner-qualified: JVerify.check(...).
        // (Verify is JFE's alternative facade class with the same contract.)
        var scope = call.getScope();
        if (scope.isEmpty() || !(scope.get() instanceof NameExpr owner)) {
            return null;
        }
        String ownerName = owner.getNameAsString();
        if (!ownerName.equals("JVerify") && !ownerName.equals("Verify")) {
            return null;
        }
        return call.getNameAsString();
    }

    /**
     * One exceptional-behavior case:
     * a {@code throwsOn(C).postcondition(...).modifies(...)} chain statement.
     * The base call carries the guard; each chained link is a clause.
     */
    private record ThrowsOnCase(ExpressionStmt statement,
                                MethodCallExpr base,
                                List<MethodCallExpr> links) {
    }

    private record Contract(List<ExpressionStmt> preconditions,
                            List<ExpressionStmt> postconditions,
                            List<ExpressionStmt> invariants,
                            List<ExpressionStmt> decreases,
                            List<ExpressionStmt> reads,
                            List<ExpressionStmt> modifies,
                            List<ThrowsOnCase> throwsOnCases,
                            List<Statement> implementation) {
    }

    /** Unwrap parentheses (JFE's TreeInfo.skipParens). */
    private static Expression skipParens(Expression expr) {
        while (expr instanceof com.github.javaparser.ast.expr.EnclosedExpr enc) {
            expr = enc.getInner();
        }
        return expr;
    }

    /**
     * The base {@code throwsOn(...)} call when {@code expr} is a chained
     * exceptional-behavior case
     * ({@code throwsOn(C).postcondition(...).modifies(...)}), or null when it
     * is not. Clause links are appended to {@code links} in source order.
     * Grouping is structural: a case is one statement, its clauses chain off
     * the handle {@code throwsOn} returns (JFE's throwsOnChainBase; skipParens
     * keeps a parenthesized base one chain rather than a detached handle).
     */
    private static MethodCallExpr throwsOnChainBase(Expression expr, List<MethodCallExpr> links) {
        if (!(expr instanceof MethodCallExpr call)) {
            return null;
        }
        var stack = new ArrayDeque<MethodCallExpr>();
        var cur = call;
        while (cur.getScope().isPresent()
                && skipParens(cur.getScope().get()) instanceof MethodCallExpr inner) {
            stack.push(cur);
            cur = inner;
        }
        if (!"throwsOn".equals(jverifyCallName(cur))) {
            return null;
        }
        while (!stack.isEmpty()) {
            links.add(stack.pop());
        }
        return cur;
    }

    private Contract extractContract(List<Statement> statements) {
        var pre = new ArrayList<ExpressionStmt>();
        var post = new ArrayList<ExpressionStmt>();
        var inv = new ArrayList<ExpressionStmt>();
        var dec = new ArrayList<ExpressionStmt>();
        var reads = new ArrayList<ExpressionStmt>();
        var modifies = new ArrayList<ExpressionStmt>();
        // The decompiler hoists a local's declaration to the method top when
        // its first store happens later (`int result;` above the contract
        // calls). Such bare declarations are not contract statements but must
        // not end the header scan either; they are re-emitted ahead of the
        // implementation (declaration-before-use is preserved: they carry no
        // initializer, so ordering among them is irrelevant).
        var hoistedDeclarations = new ArrayList<Statement>();
        var throwsOnCases = new ArrayList<ThrowsOnCase>();
        int i = 0;
        for (; i < statements.size(); i++) {
            if (!(statements.get(i) instanceof ExpressionStmt es)) {
                break;
            }
            if (es.getExpression() instanceof VariableDeclarationExpr declExpr
                    && declExpr.getVariables().stream().allMatch(v -> v.getInitializer().isEmpty())) {
                hoistedDeclarations.add(es);
                continue;
            }
            // An exceptional behavior case is a call chain rooted at
            // `throwsOn`; recognize it before the flat-clause dispatch below,
            // whose name switch would otherwise mistake the chain's outermost
            // `.postcondition(...)`/`.modifies(...)` link for the normal-exit
            // clause of the same name (JFE's getContract ordering).
            var caseLinks = new ArrayList<MethodCallExpr>();
            var caseBase = throwsOnChainBase(es.getExpression(), caseLinks);
            if (caseBase != null) {
                if (caseBase.getArguments().size() != 1) {
                    throw new JavaViolationException("A throwsOn call must have exactly one argument");
                }
                for (var link : caseLinks) {
                    String linkName = link.getNameAsString();
                    if (link.getArguments().size() != 1) {
                        throw new JavaViolationException(
                                "A throwsOn case's " + linkName + " clause must have exactly one argument");
                    }
                    if (!linkName.equals("postcondition") && !linkName.equals("modifies")) {
                        throw new JavaViolationException(
                                "Unsupported throwsOn case clause: " + linkName);
                    }
                }
                throwsOnCases.add(new ThrowsOnCase(es, caseBase, caseLinks));
                continue;
            }
            String name = jverifyCallName(es.getExpression());
            if (name == null || !isHeaderContractName(name)) {
                break;
            }
            switch (name) {
                case "precondition" -> pre.add(es);
                case "postcondition" -> post.add(es);
                case "invariant" -> inv.add(es);
                case "decreases" -> dec.add(es);
                case "reads" -> reads.add(es);
                case "modifies" -> modifies.add(es);
                default -> throw new IllegalStateException(name);
            }
        }
        var implementation = new ArrayList<Statement>(hoistedDeclarations);
        implementation.addAll(statements.subList(i, statements.size()));
        return new Contract(pre, post, inv, dec, reads, modifies, throwsOnCases, implementation);
    }

    private MethodCallExpr specCall(ExpressionStmt es) {
        return (MethodCallExpr) es.getExpression();
    }

    /** The single argument of a spec call, with arity checking mirroring JFE. */
    private Expression specArgument(ExpressionStmt es, String what) {
        var call = specCall(es);
        if (call.getArguments().size() != 1) {
            throw new JavaViolationException("A call to '" + what + "' may have only one argument");
        }
        return call.getArgument(0);
    }

    // --- Types ---

    private HighType translateType(Type type) {
        if (type instanceof PrimitiveType prim) {
            // @Nat / @Unbounded type-use annotations are recovered from the
            // classfile's RuntimeInvisibleTypeAnnotations by the decompiler
            // and honored per JFE's integerType mapping.
            boolean isNat = type.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().equals("Nat"));
            boolean isUnbounded = type.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().equals("Unbounded"));
            return switch (prim.getType()) {
                case INT -> integerType(isNat, isUnbounded, "int32", "nat31");
                case SHORT -> integerType(isNat, isUnbounded, "int16", "nat15");
                case BYTE -> integerType(isNat, isUnbounded, "int8", "nat7");
                case LONG -> integerType(isNat, isUnbounded, "int64", "nat63");
                case CHAR -> new HighType.UserDefined(ident("char"));
                case BOOLEAN -> new HighType.TBool();
                default -> throw new JavaViolationException("Unsupported type: " + type);
            };
        }
        // Class / interface / record references: an opaque Laurel composite
        // sort named by the fully qualified source type (JFE's scheme; Strata
        // treats unbound composites as uninterpreted reference sorts).
        if (type instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cls) {
            // A type variable (recovered by the decompiler from the Signature
            // attribute) refuses exactly as JFE's TypeVar arm does.
            if (cls.getScope().isEmpty() && currentTypeParams.contains(cls.getNameAsString())) {
                throw new JavaViolationException("Unsupported type: " + cls.getNameAsString());
            }
            String sortName = qualifyTypeName(cls.getNameWithScope());
            referencedCompositeTypes.add(sortName);
            return new HighType.UserDefined(ident(sortName));
        }
        throw new JavaViolationException("Unsupported type: " + type);
    }

    /** JFE's integerType: @Unbounded -> unbounded int (or nat), else the fixed-width sorts. */
    private HighType integerType(boolean isNat, boolean isUnbounded, String bounded, String boundedNat) {
        if (isUnbounded) {
            return isNat ? new HighType.UserDefined(ident("nat")) : new HighType.TInt();
        }
        return new HighType.UserDefined(ident(isNat ? boundedNat : bounded));
    }

    // --- Exception hierarchy (port of JFE's superclassOf/isSubclassOf/LCA/linkExtends) ---

    /**
     * Resolve a decompiled class reference to an fqn: this compilation's
     * classes first, then the unit's single-type imports, then an already-
     * qualified name as-is, then {@code java.lang} (the decompiler strips
     * that import).
     */
    private String resolveClassFqn(String name) {
        String qualified = qualifyTypeName(name);
        if (knownClasses.containsKey(qualified) || qualified.indexOf('.') >= 0) {
            return qualified;
        }
        String imported = currentImports.get(qualified);
        if (imported != null) {
            return imported;
        }
        try {
            Class.forName("java.lang." + qualified, false, getClass().getClassLoader());
            return "java.lang." + qualified;
        } catch (ClassNotFoundException e) {
            return qualified;
        }
    }

    /**
     * The direct superclass fqn of {@code fqn}, or null at the top of the
     * chain (or for an unresolvable class). Program classes resolve from
     * their classfiles; everything else (JCL, libraries on the verifier's own
     * classpath) via reflection without initialization.
     */
    private String superclassOf(String fqn) {
        String prog = programSuper.get(fqn);
        if (prog != null) {
            return prog;
        }
        try {
            Class<?> c = Class.forName(fqn, false, getClass().getClassLoader());
            Class<?> s = c.getSuperclass();
            return s == null ? null : s.getName().replace('$', '.');
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** True iff {@code fqn} is {@code ancestor} or below it on the superclass chain. */
    private boolean isSubclassOf(String fqn, String ancestor) {
        for (String c = fqn; c != null; c = superclassOf(c)) {
            if (c.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code fqn} has {@code java.lang.Throwable} on its superclass chain. */
    private boolean isThrowableSubtype(String fqn) {
        return isSubclassOf(fqn, "java.lang.Throwable");
    }

    /**
     * Least common ancestor of two classes along their superclass chains.
     * Interfaces are irrelevant: thrown types are classes with single
     * inheritance. Terminates at worst at {@code java.lang.Object}.
     */
    private String leastCommonAncestor(String a, String b) {
        var bChain = new java.util.LinkedHashSet<String>();
        for (String c = b; c != null; c = superclassOf(c)) {
            bChain.add(c);
        }
        for (String c = a; c != null; c = superclassOf(c)) {
            if (bChain.contains(c)) {
                return c;
            }
        }
        throw new JavaViolationException("thrown types share no common ancestor");
    }

    /**
     * Record that composite {@code child} extends {@code parent}, declaring
     * both sorts. Membership in referencedCompositeTypes and the edge in
     * compositeExtends always move together, so a linked child cannot
     * silently miss its declaration; a conflicting parent edge is refused
     * loudly (JFE's linkExtends).
     */
    private void linkExtends(String childSort, String parentSort) {
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
     * Declare {@code fqn} and its superclass chain up to and including
     * {@code java.lang.Throwable} (or the top of the chain if Throwable is
     * not an ancestor) as composites with their extends links. Registering
     * all the way up -- not just to a declared join -- is what keeps every
     * {@code is} test between exception types meaningful: a caller may catch
     * any supertype of a declared type, and that guard needs the chain
     * linked (JFE's registerHierarchyUpToThrowable).
     */
    private void registerHierarchyUpToThrowable(String fqn) {
        String stop = fqn;
        for (String c = fqn; c != null; c = superclassOf(c)) {
            stop = c;
            if (c.equals("java.lang.Throwable")) {
                break;
            }
        }
        for (String c = fqn; c != null && !c.equals(stop); c = superclassOf(c)) {
            String parent = superclassOf(c);
            if (parent == null) {
                break;
            }
            linkExtends(c, parent);
        }
        referencedCompositeTypes.add(stop);
    }

    // --- Per-class compilation ---

    private void compileUnit(DecompiledClass cls) {
        String fqn = cls.internalName().replace('/', '.').replace('$', '.');
        // Single-type imports of the decompiled unit, for resolving simple
        // exception names (e.g. `throws IOException`) to fqns.
        var imports = new HashMap<String, String>();
        for (var imp : cls.unit().getImports()) {
            if (!imp.isAsterisk() && !imp.isStatic()) {
                imports.put(simpleName(imp.getNameAsString()), imp.getNameAsString());
            }
        }
        currentImports = imports;
        for (TypeDeclaration<?> decl : cls.unit().getTypes()) {
            compileType(fqn, decl);
        }
        registerUntranslatedMethods(cls);
    }

    /**
     * Reproduce JFE's missing-contract warning for the implicit/explicit
     * {@code super()} call of an exception class's constructor: the
     * superclass constructor is a library method with no contract, so JFE's
     * MissingContractCompiler warns at the synthesized constructor's
     * position, which is the class declaration (rendered at the {@code class}
     * keyword). The general mechanism (warn on every un-contracted library
     * call) lives behind other blockers (see MissingContracts); this covers
     * the slice the exception corpus pins: a program class whose superclass
     * is a JCL/library Throwable subtype.
     */
    private void warnMissingSuperInitContract(String fqn, TypeDeclaration<?> decl) {
        String superFqn = programSuper.get(fqn);
        if (superFqn == null || sourceTypes.containsKey(superFqn)
                || !isThrowableSubtype(superFqn)) {
            return;
        }
        reporter.reportWarning(currentUri, classKeywordRange(decl),
                "missing contract for method '<init>' in class '" + superFqn + "'");
    }

    /**
     * A 1-character range at the {@code class}/{@code record}/... keyword of
     * a type declaration -- javac sets a JCClassDecl's position at the
     * keyword, and JFE's frontend diagnostics render there. Recovered
     * lexically: the last keyword occurrence directly naming this type.
     */
    private Range classKeywordRange(TypeDeclaration<?> decl) {
        if (currentSource == null || currentIndex == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b(class|interface|enum|record)\\s+"
                        + java.util.regex.Pattern.quote(decl.getNameAsString()) + "\\b")
                .matcher(currentSource);
        if (!matcher.find()) {
            return Reporter.NO_RANGE;
        }
        var start = currentIndex.position(matcher.start());
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    /**
     * Count parity with JFE: its VerifyAnnotationCompiler registers EVERY
     * method declaration — constructors (including the implicit one javac
     * synthesizes) and instance methods — as Verified even though only static
     * methods are translated. Constructors are skipped by the decompiler, so
     * they are registered here straight from the classfiles, with an empty
     * (line-0) interval so no real diagnostic ever attributes to them.
     */
    private void registerUntranslatedMethods(DecompiledClass cls) {
        if (currentUri == null) {
            return;
        }
        var models = new ArrayList<java.lang.classfile.ClassModel>();
        models.add(cls.classModel());
        models.addAll(cls.nestedModels());
        var tree = methodStatuses.computeIfAbsent(currentUri, k -> new IntervalTree<>());
        for (var cm : models) {
            String modelFqn = cm.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            if (contractClasses.contains(modelFqn)) {
                continue; // @Contract classes are spec definitions, not user code
            }
            // Constructors are never translated (JFE defers them: emitting
            // one would embed '<init>' in the name and expose the
            // synthesized super() chain). A USER-WRITTEN constructor is
            // demoted to Skipped -- its (unchecked) contract must not be
            // reported green; the implicit constructor's vacuous Verified is
            // sound and gives JFE's "+1 per class" convention. The decompiled
            // AST distinguishes them: the decompiler drops the trivial form,
            // so a surviving ConstructorDeclaration means user-written.
            // The decompiler drops a trivial explicit ctor (identical bytecode
            // to the implicit one), so the AST alone under-detects. The
            // classfile still tells them apart: -Xjcov writes CharacterRange
            // entries only for SOURCE constructs, so an explicit constructor
            // (trivial or not) carries a CRT and the implicit one does not.
            boolean astExplicitCtor = sourceTypes.get(modelFqn) != null
                    && sourceTypes.get(modelFqn).getMembers().stream()
                            .anyMatch(mem -> mem instanceof com.github.javaparser.ast.body.ConstructorDeclaration);
            // The implicit constructor inherits its class's opt-out (JFE
            // seeds the interval entry from the @Verify stack): an opted-out
            // class's "+1 per class" is a Skipped, not a Verified.
            var classAnno = verifyAnnotation(cm);
            boolean classOptedOut = classAnno != null
                    ? !verifyValue(classAnno, "value", true)
                    : !verifyByDefault;
            for (var m : cm.methods()) {
                String name = m.methodName().stringValue();
                if (!name.equals("<init>")) {
                    continue;
                }
                if ((m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                        | java.lang.classfile.ClassFile.ACC_BRIDGE)) != 0) {
                    continue;
                }
                boolean crtExplicitCtor = m.code()
                        .map(code -> code.attributes().stream()
                                .anyMatch(a -> a instanceof
                                        java.lang.classfile.attribute.CharacterRangeTableAttribute crt
                                        && !crt.characterRangeTable().isEmpty()))
                        .orElse(false);
                tree.insert(0, 0, new MethodVerificationStatus(name, Reporter.NO_RANGE,
                        astExplicitCtor || crtExplicitCtor || classOptedOut
                                ? MethodVerificationStatus.VerificationStatus.Skipped
                                : MethodVerificationStatus.VerificationStatus.Verified));
            }
        }
    }

    // --- @Verify stack (port of JFE's VerifyAnnotationCompiler modes) ---

    private enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }

    private final Deque<ShouldVerifyMode> shouldVerifies = new ArrayDeque<>();

    private boolean shouldVerify() {
        for (var mode : shouldVerifies) { // Deque iterates newest-first
            if (mode == ShouldVerifyMode.AlwaysYes || mode == ShouldVerifyMode.DefaultYes) {
                return true;
            }
            if (mode == ShouldVerifyMode.AlwaysNo || mode == ShouldVerifyMode.DefaultNo) {
                return false;
            }
        }
        throw new IllegalStateException("shouldVerify stack must have a decided base");
    }

    private void pushShouldVerify(ShouldVerifyMode mode) {
        var top = shouldVerifies.peek();
        if (top == ShouldVerifyMode.AlwaysYes || top == ShouldVerifyMode.AlwaysNo) {
            shouldVerifies.push(top);
        } else if (mode == ShouldVerifyMode.Inherit) {
            shouldVerifies.push(top != null ? top : ShouldVerifyMode.Inherit);
        } else {
            shouldVerifies.push(mode);
        }
    }

    /** The ShouldVerifyMode of a declaration's @Verify annotation, or Inherit. */
    private static ShouldVerifyMode verifyModeOf(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> decl) {
        var verify = decl.getAnnotationByName("Verify");
        if (verify.isEmpty()) {
            return ShouldVerifyMode.Inherit;
        }
        boolean should = true;
        boolean overrideChildren = false; // @Verify's declared default
        var ann = verify.get();
        if (ann instanceof com.github.javaparser.ast.expr.SingleMemberAnnotationExpr single
                && single.getMemberValue() instanceof BooleanLiteralExpr b) {
            should = b.getValue();
        } else if (ann instanceof com.github.javaparser.ast.expr.NormalAnnotationExpr normal) {
            for (var pair : normal.getPairs()) {
                if (pair.getValue() instanceof BooleanLiteralExpr b) {
                    switch (pair.getNameAsString()) {
                        case "value" -> should = b.getValue();
                        case "overrideChildren" -> overrideChildren = b.getValue();
                        default -> { }
                    }
                }
            }
        }
        if (should) {
            return overrideChildren ? ShouldVerifyMode.AlwaysYes : ShouldVerifyMode.DefaultYes;
        }
        return overrideChildren ? ShouldVerifyMode.AlwaysNo : ShouldVerifyMode.DefaultNo;
    }

    private void compileType(String fqn, TypeDeclaration<?> decl) {
        if (contractClasses.contains(fqn)) {
            return; // consumed as a contract definition, not user code
        }
        warnMissingSuperInitContract(fqn, decl);
        pushShouldVerify(verifyModeOf(decl));
        try {
            // Local classes hoisted from different methods may share a name
            // (JFE keys on symbols; here the repeat count disambiguates).
            var nestedNameCounts = new HashMap<String, Integer>();
            for (var member : decl.getMembers()) {
                if (member instanceof MethodDeclaration method) {
                    compileMethod(fqn, method);
                }
                // Nested TypeDeclarations: their methods belong to their own
                // classfile pool entries; Decompiler re-nests them here,
                // so walk them too under their own name.
                if (member instanceof TypeDeclaration<?> nested) {
                    // A hoisted ANONYMOUS class is deferred exactly as JFE
                    // defers it: methods demote to Skipped, no diagnostic.
                    if (nested.containsData(org.strata.java.bytecode.frontend.Decompiler.ANONYMOUS)) {
                        for (var m : nested.getMethods()) {
                            if (m.getBody().isPresent()) {
                                recordStatus(m, methodRange(m),
                                        MethodVerificationStatus.VerificationStatus.Skipped);
                            }
                        }
                        continue;
                    }
                    compileType(nestedFqn(fqn, nested, nestedNameCounts), nested);
                }
            }
        } finally {
            shouldVerifies.pop();
        }
    }

    private static String nestedFqn(String outerFqn, TypeDeclaration<?> nested,
                                    HashMap<String, Integer> counts) {
        String name = nested.getNameAsString();
        int occurrence = counts.merge(name, 1, Integer::sum);
        return outerFqn + "." + name + (occurrence > 1 ? "$" + occurrence : "");
    }

    private void compileMethod(String fqn, MethodDeclaration method) {
        pushShouldVerify(verifyModeOf(method));
        boolean verify = shouldVerify();
        shouldVerifies.pop();
        var verifiedOrSkipped = verify
                ? MethodVerificationStatus.VerificationStatus.Verified
                : MethodVerificationStatus.VerificationStatus.Skipped;

        // Methods of anonymous/local classes declared inside this method's
        // body: JFE defers them (no qualified enclosing name -> the sort is
        // only reachable by flatname) and demotes them to Skipped silently.
        for (var anon : method.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)) {
            anon.getAnonymousClassBody().ifPresent(members -> {
                for (var m : members) {
                    if (m instanceof MethodDeclaration anonMethod && anonMethod.getBody().isPresent()) {
                        recordStatus(anonMethod, methodRange(anonMethod),
                                MethodVerificationStatus.VerificationStatus.Skipped);
                    }
                }
            });
        }
        for (var local : method.findAll(com.github.javaparser.ast.stmt.LocalClassDeclarationStmt.class)) {
            for (var m : local.getClassDeclaration().getMembers()) {
                if (m instanceof MethodDeclaration localMethod && localMethod.getBody().isPresent()) {
                    recordStatus(localMethod, methodRange(localMethod),
                            MethodVerificationStatus.VerificationStatus.Skipped);
                }
            }
        }

        boolean isInterpretEntry = method.getAnnotationByName("InterpretEntry").isPresent();
        if (!method.isStatic() && isInterpretEntry) {
            // JFE rejects the marker on instance methods with a clear
            // diagnostic instead of silently ignoring it.
            reporter.reportError(currentUri, namePointRange(method),
                    "@InterpretEntry method '" + method.getNameAsString() + "' must be static");
            recordStatus(method, methodRange(method),
                    MethodVerificationStatus.VerificationStatus.Skipped);
            return;
        }
        if (method.getBody().isEmpty()) {
            if (method.getAnnotationByName("EmptyContract").isPresent()) {
                recordStatus(method, methodRange(method), verifiedOrSkipped);
                return;
            }
            // JFE attempts body-less (abstract/interface) methods too: a
            // refusal reports at the declaration, but no interval entry is
            // seeded (JFE seeds only methods with bodies), so counts stay
            // unaffected and nothing is emitted.
            probeBodylessMethod(fqn, method, verify);
            return;
        }
        // Ordinal among same-name siblings: distinguishes overload
        // declarations, which share a by-name key.
        int ordinal = 0;
        if (method.getParentNode().orElse(null)
                instanceof com.github.javaparser.ast.body.TypeDeclaration<?> td) {
            ordinal = (int) td.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(method.getNameAsString()))
                    .toList().indexOf(method);
        }
        var info = new MethodInfo(fqn, method.getNameAsString(), method.isStatic(),
                currentUri, namePointRange(method), ordinal, !verify);
        methodInfos.put(info.key(), info);
        currentMethodInfo = info;
        currentClassFqn = fqn;
        try {
            translateMethod(fqn, method, verify, isInterpretEntry);
        } catch (ReportedViolation e) {
            // Already reported at its own source position; only record the skip.
            info.refused = true;
            recordRefusedStatus(info, method);
        } catch (JavaViolationException e) {
            info.refused = true;
            reportRefusal(info, e.getMessage());
            recordRefusedStatus(info, method);
        } finally {
            currentMethodInfo = null;
        }
    }

    /**
     * JFE-parity refusal detection for a body-less method: the gates JFE's
     * translateMethod hits before the body (declaration overload, parameter
     * and return types, type variables). Nothing is recorded or emitted --
     * only the diagnostic is reproduced.
     */
    private void probeBodylessMethod(String fqn, MethodDeclaration method, boolean verify) {
        var savedTypeParams = currentTypeParams;
        try {
            var typeParams = new java.util.HashSet<String>();
            method.getTypeParameters().forEach(tp -> typeParams.add(tp.getNameAsString()));
            if (sourceTypes.get(fqn)
                    instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid) {
                cid.getTypeParameters().forEach(tp -> typeParams.add(tp.getNameAsString()));
            }
            currentTypeParams = typeParams;
            refuseIfOverloaded(fqn, method.getNameAsString(), method.isStatic());
            for (Parameter param : method.getParameters()) {
                translateType(param.getType());
            }
            if (!method.getType().isVoidType()) {
                translateType(method.getType());
            }
        } catch (JavaViolationException e) {
            var info = new MethodInfo(fqn, method.getNameAsString(), method.isStatic(),
                    currentUri, bodylessNamePoint(method), 0, !verify);
            info.refused = true;
            methodInfos.putIfAbsent(info.key(), info);
            reportRefusal(info, e.getMessage());
        } finally {
            currentTypeParams = savedTypeParams;
        }
    }

    /**
     * Declaration anchor for a body-less method: no CRT span exists, so the
     * name position is recovered by the declaration's shape -- an ABSTRACT
     * declaration ends its parameter list with {@code ;} (an implementation
     * opens {@code '{'}), and is not preceded by a receiver dot (a call).
     */
    private Range bodylessNamePoint(MethodDeclaration method) {
        if (currentSource == null || currentIndex == null) {
            return Reporter.NO_RANGE;
        }
        var matcher = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(method.getNameAsString())
                        + "\\s*\\([^)]*\\)\\s*;")
                .matcher(currentSource);
        while (matcher.find()) {
            int at = matcher.start();
            if (at > 0 && currentSource.charAt(at - 1) == '.') {
                continue; // a call through a receiver, not a declaration
            }
            var start = currentIndex.position(at);
            return new Range(start,
                    new org.strata.jverify.common.Position(start.line(), start.character() + 1));
        }
        return Reporter.NO_RANGE;
    }

    /**
     * Report a method's translation refusal with JFE's severity asymmetry: a
     * static method's failure is an error ({@code translatorError={0}}), an
     * instance method's a warning ({@code instanceMethodNotVerified}) --
     * instance support is deliberately partial, so a refusal demotes to an
     * honest Skipped instead of failing the build. Both anchor at the refused
     * method's own name (JFE's JCMethodDecl preferred position). The
     * diagnostic is deduped per (method, reason); the demotion never is.
     */
    private void reportRefusal(MethodInfo info, String reason) {
        if (info.quiet) {
            return; // opted-out methods demote silently (JFE never translates them loudly)
        }
        if (!reportedRefusals.computeIfAbsent(info.key(), k -> new java.util.LinkedHashSet<>())
                .add(reason)) {
            return;
        }
        if (info.isStatic) {
            reporter.reportError(info.uri, info.namePoint, reason);
        } else {
            reporter.reportWarning(info.uri, info.namePoint,
                    "instance method '" + info.name + "' was not verified: " + reason);
        }
    }

    private void recordRefusedStatus(MethodInfo info, MethodDeclaration method) {
        var range = methodRange(method);
        var status = new MethodVerificationStatus(method.getNameAsString(),
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                MethodVerificationStatus.VerificationStatus.Skipped);
        info.status = status;
        recordStatus(method, range, status);
    }

    /**
     * A translation refusal whose diagnostic was already emitted, positioned
     * at the offending expression (JFE reports some refusals at the tree
     * rather than the enclosing method). The catch above must not re-report.
     */
    static final class ReportedViolation extends JavaViolationException {
        ReportedViolation() {
            super("already reported");
        }
    }

    /**
     * A 1-character range at the method's name — how JFE's javac-backed
     * frontend diagnostics present (a JCDiagnostic renders as
     * {@code line:col-line:col+1} at the tree's preferred position, which is
     * the name for method declarations). The name position is recovered
     * lexically: the last {@code name(} occurrence before the body's first
     * statement.
     */
    private Range namePointRange(MethodDeclaration method) {
        if (currentSource == null) {
            return Reporter.NO_RANGE;
        }
        var range = methodRange(method);
        var pattern = java.util.regex.Pattern
                .compile("\\b" + java.util.regex.Pattern.quote(method.getNameAsString()) + "\\s*\\(");
        int namePos = -1;
        if (range == UNKNOWN_RANGE) {
            // No CRT span to anchor on (an empty body). Fall back to the
            // declaration's ordinal among same-named siblings: source order
            // matches classfile order, so the k-th declaration is the k-th
            // occurrence of `name(` in the file.
            int ordinal = 0;
            if (method.getParentNode().orElse(null)
                    instanceof com.github.javaparser.ast.body.TypeDeclaration<?> td) {
                ordinal = (int) td.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(method.getNameAsString()))
                        .toList().indexOf(method);
            }
            var matcher = pattern.matcher(currentSource);
            for (int i = 0; matcher.find(); i++) {
                if (i == ordinal) {
                    namePos = matcher.start();
                    break;
                }
            }
            if (namePos < 0) {
                return Reporter.NO_RANGE;
            }
        } else {
            int bodyStart = (int) range.range().start().byteIdx();
            var matcher = pattern.matcher(currentSource);
            while (matcher.find() && matcher.start() < bodyStart) {
                namePos = matcher.start();
            }
            if (namePos < 0) {
                return positionRange(range);
            }
        }
        var start = currentIndex.position(namePos);
        return new Range(start, new org.strata.jverify.common.Position(start.line(), start.character() + 1));
    }

    /**
     * Port of JFE's bodyPotentiallyWritesHeap: an instance-field write
     * (assignment / compound assignment / ++ / -- on a field access), or a
     * call to a non-JVerify method not known to be @Pure. Spec-call subtrees
     * are excluded (a spec describes the method, not its runtime effect).
     */
    private boolean bodyPotentiallyWritesHeap(MethodDeclaration method) {
        var body = method.getBody().orElse(null);
        if (body == null) {
            return false;
        }
        return body.stream().anyMatch(n -> {
            // Skip anything that is (or sits under) a JVerify spec call --
            // including a throwsOn(...).postcondition(...) chain, whose links
            // are spec clauses even though their scope is not a NameExpr.
            for (com.github.javaparser.ast.Node p = n; p != null; p = p.getParentNode().orElse(null)) {
                if (p instanceof MethodCallExpr mc && isSpecTree(mc)) {
                    return false;
                }
            }
            if (n instanceof AssignExpr assign
                    && assign.getTarget() instanceof com.github.javaparser.ast.expr.FieldAccessExpr) {
                return true;
            }
            if (n instanceof UnaryExpr unary
                    && unary.getExpression() instanceof com.github.javaparser.ast.expr.FieldAccessExpr
                    && switch (unary.getOperator()) {
                        case PREFIX_INCREMENT, PREFIX_DECREMENT,
                             POSTFIX_INCREMENT, POSTFIX_DECREMENT -> true;
                        default -> false;
                    }) {
                return true;
            }
            if (n instanceof MethodCallExpr call && !isJVerifyCall(call)) {
                var scope = call.getScope();
                if (scope.isPresent() && scope.get() instanceof NameExpr owner) {
                    String fqn = knownClasses.get(owner.getNameAsString());
                    if (fqn != null) {
                        return !pureMethods.getOrDefault(fqn + "_" + call.getNameAsString(), false);
                    }
                    // An instance call on a typed receiver: pure iff the
                    // resolved callee is @Pure (same map, keyed by its
                    // declaring class).
                    String receiverFqn = staticTypeFqn(owner);
                    if (receiverFqn != null) {
                        var callee = findCallee(receiverFqn, call.getNameAsString());
                        if (callee != null) {
                            return !pureMethods.getOrDefault(
                                    callee.declaringFqn() + "_" + call.getNameAsString(), false);
                        }
                    }
                }
                return true; // unknown callee: conservatively heap-writing
            }
            return false;
        });
    }

    private static boolean isJVerifyCall(MethodCallExpr call) {
        return jverifyCallName(call) != null;
    }

    /** A JVerify spec call, either flat or a throwsOn chain (any link). */
    private static boolean isSpecTree(MethodCallExpr call) {
        if (isJVerifyCall(call)) {
            return true;
        }
        return throwsOnChainBase(call, new ArrayList<>()) != null;
    }

    private static boolean containsLoop(MethodDeclaration method) {
        return method.findFirst(WhileStmt.class).isPresent()
                || method.findFirst(ForStmt.class).isPresent()
                || method.findFirst(DoStmt.class).isPresent();
    }

    /** The span envelope of a method: min..max over its statements' spans. */
    private FileRange methodRange(MethodDeclaration method) {
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (var s : method.findAll(Statement.class)) {
            if (!s.containsData(Decompiler.SPAN) || currentIndex == null) {
                continue;
            }
            var span = s.getData(Decompiler.SPAN);
            min = Math.min(min, currentIndex.offset(span.beginLine(), span.beginCol()));
            max = Math.max(max, currentIndex.offset(span.endLine(), span.endCol()));
        }
        if (max < 0) {
            return UNKNOWN_RANGE;
        }
        return rangeOf(min, max);
    }

    private void recordStatus(MethodDeclaration method, FileRange range,
                              MethodVerificationStatus.VerificationStatus status) {
        recordStatus(method, range, new MethodVerificationStatus(method.getNameAsString(),
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range), status));
    }

    /**
     * Insert an existing status record (shared with a {@link MethodInfo}, so
     * the refusal-propagation fixpoint can later demote it in place).
     */
    private void recordStatus(MethodDeclaration method, FileRange range, MethodVerificationStatus status) {
        if (currentUri == null) {
            return;
        }
        var tree = methodStatuses.computeIfAbsent(currentUri, k -> new IntervalTree<>());
        if (range == UNKNOWN_RANGE) {
            // A method with no CRT spans (typically an empty body) still
            // counts — JFE registers every non-synthetic method declaration.
            // The line-0 interval keeps it out of diagnostic attribution.
            tree.insert(0, 0, status);
            return;
        }
        var posRange = positionRange(range);
        tree.insert(posRange.start().line(), posRange.end().line(), status);
    }

    private void translateMethod(String classFqn, MethodDeclaration method,
                                 boolean verify, boolean isInterpretEntry) {
        boolean isStatic = method.isStatic();
        // An instance procedure lives INSIDE its composite under the bare
        // method name (Strata's LiftInstanceProcedures lifts it to
        // <Composite>$<method>); a static keeps the mangled top-level name.
        String methodName = isStatic
                ? classFqn + "_" + method.getNameAsString()
                : method.getNameAsString();
        // Overloading refusal at the declaration (JFE runs overloadRefusal
        // first in translateMethod): a Laurel procedure name carries no
        // signature, so a same-named sibling makes by-name resolution
        // ambiguous.
        refuseIfOverloaded(classFqn, method.getNameAsString(), isStatic);

        if (isInterpretEntry && !method.getParameters().isEmpty()) {
            throw new JavaViolationException(
                    "@InterpretEntry method '" + method.getNameAsString() + "' must take no arguments");
        }

        // Type parameters in scope: the decompiler recovers them from the
        // Signature attribute, and any use refuses with JFE's message
        // (JFE: translateType's "Unsupported type: T" for a TypeVar).
        var typeParams = new java.util.HashSet<String>();
        method.getTypeParameters().forEach(tp -> typeParams.add(tp.getNameAsString()));
        if (sourceTypes.get(classFqn)
                instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid) {
            cid.getTypeParameters().forEach(tp -> typeParams.add(tp.getNameAsString()));
        }
        currentTypeParams = typeParams;
        // The decompiler marks bytecode it could not structure with
        // "TODO unsupported: <OP>" comments (irreducible or infinite-loop
        // control flow). The reconstructed statement list is not the real
        // method; translating it would be unsound.
        var unsupported = method.getBody().orElseThrow().stream()
                .map(n -> n.getComment().map(c -> c.getContent()).orElse(""))
                .filter(c -> c.contains("TODO unsupported"))
                .findFirst();
        if (unsupported.isPresent()) {
            throw new JavaViolationException(
                    "unsupported control flow (decompiler could not structure: "
                            + unsupported.get().strip() + ")");
        }

        var inputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
        if (!isStatic) {
            // REQUIRED by Strata, not a choice here: LiftInstanceProcedures
            // rewrites `obj#method(args)` by PREPENDING the receiver, so the
            // lifted procedure must declare it first for arities to line up.
            referencedCompositeTypes.add(classFqn);
            inputs.add(new org.strata.jverify.laurel.Parameter(
                    ident(LAUREL_SELF_BINDING), node(new HighType.UserDefined(ident(classFqn)))));
        }
        for (Parameter param : method.getParameters()) {
            inputs.add(new org.strata.jverify.laurel.Parameter(
                    ident(param.getNameAsString()), node(translateType(param.getType()))));
        }

        var outputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
        currentReturnSort = null;
        if (!method.getType().isVoidType()) {
            outputs.add(new org.strata.jverify.laurel.Parameter(
                    ident(LAUREL_RESULT_BINDING), node(translateType(method.getType()))));
            if (method.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot) {
                currentReturnSort = qualifyTypeName(cot.getNameWithScope());
            }
        }

        // The declared exceptional exit: `throws E1, E2` joins to the least
        // common ancestor (Laurel declares at most one exceptional exit).
        // Each thrown class is declared with its chain up to Throwable -- a
        // caller may catch any supertype, and that guard's `is` test needs
        // the chain linked (JFE's translateThrowsClause). The decompiler
        // reads the clause from the classfile's Exceptions attribute.
        currentThrowsJoin = null;
        enclosingCatchTypes.clear();
        Optional<AstNode<HighType>> throwsType = Optional.empty();
        Optional<Identifier> throwsBinding = Optional.empty();
        if (!method.getThrownExceptions().isEmpty()) {
            List<String> thrown = new ArrayList<>();
            for (var t : method.getThrownExceptions()) {
                if (!(t instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot)
                        || method.getTypeParameters().stream()
                                .anyMatch(tp -> tp.getNameAsString().equals(cot.getNameWithScope()))) {
                    // e.g. a generic exception parameter: `<X extends Exception> ... throws X`.
                    throw new JavaViolationException(
                            "Unsupported thrown type in throws clause: " + t);
                }
                thrown.add(resolveClassFqn(cot.getNameWithScope()));
            }
            String join = thrown.get(0);
            for (var cls : thrown.subList(1, thrown.size())) {
                join = leastCommonAncestor(cls, join);
            }
            for (var cls : thrown) {
                registerHierarchyUpToThrowable(cls);
            }
            referencedCompositeTypes.add(join);
            currentThrowsJoin = join;
            throwsType = Optional.of(node(new HighType.UserDefined(ident(join))));
            throwsBinding = Optional.of(ident(THROWN_BINDING));
        }

        var body = method.getBody().orElseThrow();
        var contract = extractContract(body.getStatements());
        if (!contract.invariants().isEmpty()) {
            throw new JavaViolationException("invariants are not allowed in a method");
        }

        var preconditions = new ArrayList<Condition>();
        for (var pre : contract.preconditions()) {
            var stmtRange = statementRange(pre);
            var arg = specArgument(pre, "precondition");
            // BooleanSupplier form: precondition(() -> ...). JFE converts the
            // lambda body with no renames and stamps the whole argument.
            var condRange = arg instanceof com.github.javaparser.ast.expr.LambdaExpr
                    ? lambdaBodyRange(stmtRange)
                    : argumentRange(stmtRange);
            currentStatementRange = condRange;
            StmtExpr converted = inContract(() ->
                    arg instanceof com.github.javaparser.ast.expr.LambdaExpr lambda
                            ? convertLambdaBody(lambda)
                            : convertExpression(arg));
            preconditions.add(new Condition(node(converted, condRange),
                    Optional.empty(), new ConditionMode.Both()));
        }

        var postconditions = new ArrayList<Condition>();
        for (var post : contract.postconditions()) {
            var stmtRange = statementRange(post);
            var arg = specArgument(post, "postcondition");
            // JFE stamps a lambda-form postcondition with the lambda's BODY
            // range, and an expression form with the whole argument.
            var condRange = arg instanceof com.github.javaparser.ast.expr.LambdaExpr
                    ? lambdaBodyRange(stmtRange)
                    : argumentRange(stmtRange);
            currentStatementRange = condRange;
            postconditions.add(new Condition(
                    node(inContract(() -> convertPostconditionArgument(arg)), condRange),
                    Optional.empty(), new ConditionMode.Both()));
        }

        // decreases / reads: recognized (so they don't break the header
        // scan) — JFE currently drops both at Laurel emission too.
        // modifies(...): everything() maps to the wildcard (StmtExpr.All);
        // any other argument is translated as an expression (JFE scheme).
        var explicitModifies = new ArrayList<AstNode<StmtExpr>>();
        for (var mod : contract.modifies()) {
            var argRange = argumentRange(statementRange(mod));
            currentStatementRange = argRange;
            var arg = specArgument(mod, "modifies");
            if (jverifyCallName(arg) != null && jverifyCallName(arg).equals("everything")) {
                explicitModifies.add(node(new StmtExpr.All(), argRange));
            } else {
                explicitModifies.add(node(inContract(() -> convertExpression(arg)), argRange));
            }
        }

        // Exceptional behavior cases: each
        // `throwsOn(...).postcondition(...).modifies(...)` chain becomes a
        // Laurel ThrowsOnBlock. The guard converts as a plain boolean
        // expression (Strata's resolution rejects heap-reading guards with
        // its own diagnostic); a case postcondition binds the thrown value by
        // renaming its lambda parameter to the `throws (e: T)` binding; a
        // case frame entry converts exactly like a normal modifies entry
        // (JFE's scheme).
        if (!contract.throwsOnCases().isEmpty() && currentThrowsJoin == null) {
            throw new JavaViolationException(
                    "throwsOn requires the method to declare the thrown type in its throws clause");
        }
        var throwsOnBlocks = new ArrayList<org.strata.jverify.laurel.ThrowsOnBlock>();
        for (var c : contract.throwsOnCases()) {
            var stmtRange = statementRange(c.statement());
            // The guard is the base call's argument: the first parenthesized
            // group of the chain statement's text (where forcing/proof-failure
            // diagnostics land).
            var guardRange = argumentRange(stmtRange);
            currentStatementRange = guardRange;
            var guard = node(convertExpression(c.base().getArgument(0)), guardRange);
            List<Condition> casePosts = new ArrayList<>();
            List<AstNode<StmtExpr>> caseModifies = new ArrayList<>();
            currentStatementRange = stmtRange;
            for (var link : c.links()) {
                switch (link.getNameAsString()) {
                    case "postcondition" -> casePosts.add(convertCasePostcondition(link.getArgument(0)));
                    case "modifies" -> {
                        var arg = link.getArgument(0);
                        if ("everything".equals(jverifyCallName(arg))) {
                            caseModifies.add(node(new StmtExpr.All(), stmtRange));
                        } else {
                            caseModifies.add(node(convertExpression(arg), stmtRange));
                        }
                    }
                    default -> throw new IllegalStateException(link.getNameAsString());
                }
            }
            throwsOnBlocks.add(new org.strata.jverify.laurel.ThrowsOnBlock(guard, casePosts, caseModifies));
        }

        // Pre-scan throw coverage before any statement converts: the
        // decompiled try-with-resources handler places the generated close()
        // machinery BEFORE the re-throw (JFE's javac-lowered form places it
        // after), so without this the close() call's refusal would mask the
        // declared-throws diagnostic JFE reports for the same method.
        for (var statement : contract.implementation()) {
            prescanThrowCoverage(statement);
        }

        var statements = new ArrayList<AstNode<StmtExpr>>();
        for (var statement : contract.implementation()) {
            currentStatementRange = statementRange(statement);
            StmtExpr converted = convertStatement(statement);
            if (converted != null) {
                statements.add(node(converted, statementRange(statement)));
            }
        }
        StmtExpr methodBody = new StmtExpr.Block(statements, Optional.empty());

        // Body variant selection mirrors JFE: only a @Pure method with neither
        // postconditions nor modifies stays Transparent (callers see the
        // body); a @Pure method with a loop cannot be a transparent function
        // body and is emitted uninterpreted. An unverified method (@Verify
        // false / verifyByDefault off) keeps only its contract: bodyless
        // Abstract/Opaque, so callers still see the spec (JFE's
        // removeImplementation).
        boolean isPure = method.getAnnotationByName("Pure").isPresent();
        // JFE's fallback: no explicit modifies but the body may write the
        // heap (an instance-field write, or a call to a non-JVerify,
        // non-@Pure method) -> wildcard `modifies *`, sound but coarse.
        List<AstNode<StmtExpr>> modifies = !explicitModifies.isEmpty()
                ? explicitModifies
                : bodyPotentiallyWritesHeap(method)
                        ? List.of(noRange(new StmtExpr.All()))
                        : List.of();
        Body procBody;
        if (!verify) {
            procBody = postconditions.isEmpty()
                    ? new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()))
                    : new Body.Abstract(postconditions);
        } else if (isPure && containsLoop(method)) {
            if (!postconditions.isEmpty()) {
                throw new JavaViolationException(
                        "@Pure function with a loop and a postcondition is not yet supported");
            }
            procBody = new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()));
        } else if (isPure && postconditions.isEmpty() && modifies.isEmpty()) {
            procBody = new Body.Transparent(noRange(methodBody));
        } else {
            procBody = new Body.Opaque(postconditions, Optional.of(noRange(methodBody)),
                    unguardedModifies(modifies));
        }
        var proc = new Procedure(
                ident(methodName), /*typeArgs=*/List.of(), inputs, outputs, preconditions,
                Optional.empty(), procBody, Optional.empty(),
                isInterpretEntry, List.of(),
                // Exceptional contract: the declared type and binding come
                // from the Java `throws` clause, the behavior cases from the
                // throwsOn(...) contract chains.
                throwsType, throwsBinding, throwsOnBlocks);
        if (isStatic) {
            procedures.add(proc);
        } else {
            instanceProcsBySort.computeIfAbsent(classFqn, k -> new ArrayList<>()).add(proc);
        }
        var range = methodRange(method);
        var status = new MethodVerificationStatus(method.getNameAsString(),
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                verify ? MethodVerificationStatus.VerificationStatus.Verified
                       : MethodVerificationStatus.VerificationStatus.Skipped);
        recordStatus(method, range, status);
        if (currentMethodInfo != null) {
            currentMethodInfo.status = status;
            currentMethodInfo.proc = proc;
            currentMethodInfo.sort = isStatic ? null : classFqn;
        }
    }

    /**
     * JFE's declaration-site overloadRefusal: refuse any method whose class
     * declares more than one non-synthetic, non-bridge method of the same
     * name (checked against the classfile's method table, which is exactly
     * what by-name Laurel resolution would see). Statics and instance
     * methods live in different Laurel namespaces (Class?name vs the
     * composite), so only same-staticness siblings collide; opted-out
     * methods emit no procedure, so they cannot collide either.
     */
    private void refuseIfOverloaded(String classFqn, String name, boolean isStatic) {
        var cm = programClassModels.get(classFqn);
        if (cm == null) {
            return;
        }
        long count = cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .filter(m -> (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                        | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0)
                .filter(m -> ((m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) != 0)
                        == isStatic)
                .filter(m -> !isOptedOut(cm, m))
                .count();
        if (count > 1) {
            throw new JavaViolationException("overloaded method '" + name
                    + "' (enclosing class declares " + count + " methods with this name); "
                    + "overloading is not supported");
        }
    }

    /** The {@code @Verify} annotation on an element's RuntimeInvisibleAnnotations, or null. */
    private static java.lang.classfile.Annotation verifyAnnotation(
            java.lang.classfile.AttributedElement element) {
        var annos = element
                .findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .map(java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute::annotations)
                .orElse(List.of());
        for (var a : annos) {
            if (a.className().stringValue().equals("Lorg/strata/jverify/Verify;")) {
                return a;
            }
        }
        return null;
    }

    private static boolean verifyValue(java.lang.classfile.Annotation verify, String element,
                                       boolean defaultValue) {
        for (var el : verify.elements()) {
            if (el.name().stringValue().equals(element)
                    && el.value() instanceof java.lang.classfile.AnnotationValue.OfBoolean b) {
                return b.booleanValue();
            }
        }
        return defaultValue;
    }

    /**
     * JFE's resolved (stacked) opt-out status for a classfile method: the
     * method's own {@code @Verify} wins unless the class's says
     * {@code overrideChildren}; with neither, the class-level value, then
     * verify-by-default, decides. Readable from the classfile because the
     * spec library compiled against is the CLASS-retention variant.
     */
    private boolean isOptedOut(java.lang.classfile.ClassModel cm, java.lang.classfile.MethodModel m) {
        var classAnno = verifyAnnotation(cm);
        var methodAnno = verifyAnnotation(m);
        if (classAnno != null && verifyValue(classAnno, "overrideChildren", false)) {
            return !verifyValue(classAnno, "value", true);
        }
        if (methodAnno != null) {
            return !verifyValue(methodAnno, "value", true);
        }
        if (classAnno != null) {
            return !verifyValue(classAnno, "value", true);
        }
        return !verifyByDefault;
    }

    /**
     * A postcondition argument: either a boolean expression or a lambda. The
     * decompiler reconstructs the lambda from its synthetic {@code lambda$}
     * method (captures inlined), so this mirrors JFE directly: a one-parameter
     * lambda's parameter denotes the result value and is renamed to
     * {@code $result}; a zero-parameter lambda (BooleanSupplier) gets no
     * rename.
     */
    private StmtExpr convertPostconditionArgument(Expression arg) {
        if (arg instanceof com.github.javaparser.ast.expr.LambdaExpr lambda) {
            Map<String, String> lambdaRenames = lambda.getParameters().size() == 1
                    ? Map.of(lambda.getParameter(0).getNameAsString(), LAUREL_RESULT_BINDING)
                    : Map.of();
            return withRenames(lambdaRenames, () -> convertLambdaBody(lambda));
        }
        return convertExpression(arg);
    }

    /**
     * A case's {@code .postcondition(...)} property. The lambda's one
     * parameter must be typed at the method's declared throws type -- the
     * property then converts with the parameter renamed to the
     * {@code throws (e: T)} binding, so it speaks about the thrown value
     * directly. Per-type claims narrow inside the property with
     * {@code instanceof} and a cast. Requiring the declared type keeps the
     * binding's typing exact (JFE's convertCasePostcondition).
     */
    private Condition convertCasePostcondition(Expression sigExpr) {
        // A functional-interface cast may wrap the lambda; parens too.
        sigExpr = skipParens(sigExpr);
        while (sigExpr instanceof CastExpr cast) {
            sigExpr = skipParens(cast.getExpression());
        }
        if (!(sigExpr instanceof com.github.javaparser.ast.expr.LambdaExpr lambda)
                || lambda.getParameters().size() != 1) {
            throw new JavaViolationException(
                    "the argument to a throwsOn case's postcondition must be a one-parameter lambda");
        }
        var param = lambda.getParameter(0);
        String paramFqn = param.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot
                ? resolveClassFqn(cot.getNameWithScope())
                : null;
        if (paramFqn == null || !paramFqn.equals(currentThrowsJoin)) {
            throw new JavaViolationException(
                    "a throwsOn case's postcondition parameter must be typed at the method's declared throws type"
                            + (currentThrowsJoin != null ? " ('" + currentThrowsJoin + "')" : "")
                            + "; narrow with instanceof and a cast inside the property");
        }
        StmtExpr converted = withRenames(Map.of(param.getNameAsString(), THROWN_BINDING),
                () -> convertLambdaBody(lambda));
        return new Condition(node(converted), Optional.empty(), new ConditionMode.Both());
    }

    private StmtExpr withRenames(Map<String, String> extra, java.util.function.Supplier<StmtExpr> body) {
        var saved = renames;
        var merged = new HashMap<>(renames);
        merged.putAll(extra);
        renames = merged;
        try {
            return body.get();
        } finally {
            renames = saved;
        }
    }

    /** Port of JFE's convertLambdaBody: expression body or a single-return block. */
    private StmtExpr convertLambdaBody(com.github.javaparser.ast.expr.LambdaExpr lambda) {
        if (lambda.getExpressionBody().isPresent()) {
            return convertExpression(lambda.getExpressionBody().get());
        }
        if (lambda.getBody() instanceof BlockStmt blk && blk.getStatements().size() == 1
                && blk.getStatement(0) instanceof ReturnStmt ret && ret.getExpression().isPresent()) {
            return convertExpression(ret.getExpression().get());
        }
        throw new JavaViolationException("Unsupported lambda body");
    }

    // --- Statements ---

    private StmtExpr convertStatementOrEmpty(Statement statement) {
        StmtExpr converted = convertStatement(statement);
        return converted != null ? converted : new StmtExpr.Block(List.of(), Optional.empty());
    }

    private record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {
    }

    private final Deque<LabelEntry> labelStack = new ArrayDeque<>();
    private String pendingLabel = null;

    private String freshLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    /**
     * True if the loop body has a break/continue at this loop's level.
     * Mirrors JFE's scan, which stops at nested loops (they handle their own).
     */
    private static boolean containsBreakOrContinue(Statement stmt) {
        if (stmt instanceof BreakStmt || stmt instanceof ContinueStmt) {
            return true;
        }
        if (stmt instanceof WhileStmt || stmt instanceof ForStmt || stmt instanceof DoStmt) {
            return false;
        }
        for (var child : stmt.getChildNodes()) {
            if (child instanceof Statement s && containsBreakOrContinue(s)) {
                return true;
            }
        }
        return false;
    }

    private StmtExpr convertStatement(Statement statement) {
        return switch (statement) {
            case AssertStmt assertStmt ->
                    new StmtExpr.Assert(node(convertExpression(assertStmt.getCheck())), Optional.empty());
            case ExpressionStmt exprStmt -> convertExpressionStatement(exprStmt);
            case BlockStmt blk -> convertBlock(blk);
            case IfStmt ifStmt -> {
                StmtExpr cond = convertExpression(ifStmt.getCondition());
                StmtExpr thenBranch = convertNestedStatementOrEmpty(ifStmt.getThenStmt());
                Optional<AstNode<StmtExpr>> elseNode = Optional.empty();
                if (ifStmt.getElseStmt().isPresent()) {
                    StmtExpr elseStmt = convertNestedStatement(ifStmt.getElseStmt().get());
                    if (elseStmt != null) {
                        elseNode = Optional.of(node(elseStmt, statementRange(ifStmt.getElseStmt().get())));
                    }
                }
                yield new StmtExpr.IfThenElse(node(cond),
                        node(thenBranch, statementRange(ifStmt.getThenStmt())), elseNode);
            }
            case ReturnStmt retStmt -> {
                // A returned lambda/method reference: JFE's unlambda pre-pass
                // lowers it to an anonymous class and the body emits an
                // opaque allocation -- the anonymous subtype is
                // indistinguishable from the interface sort itself here, so
                // allocate the return sort directly.
                if (retStmt.getExpression().isPresent() && currentReturnSort != null
                        && (skipParens(retStmt.getExpression().get())
                                        instanceof com.github.javaparser.ast.expr.MethodReferenceExpr
                                || skipParens(retStmt.getExpression().get())
                                        instanceof com.github.javaparser.ast.expr.LambdaExpr)) {
                    referencedCompositeTypes.add(currentReturnSort);
                    yield new StmtExpr.Return(Optional.of(node(
                            new StmtExpr.New(ident(currentReturnSort), List.of()))));
                }
                Optional<AstNode<StmtExpr>> value = retStmt.getExpression().isPresent()
                        ? Optional.of(node(convertExpression(retStmt.getExpression().get())))
                        : Optional.empty();
                yield new StmtExpr.Return(value);
            }
            case WhileStmt whileStmt -> withLoopLabels(whileStmt.getBody(), (breakLbl, continueLbl) -> {
                StmtExpr cond = convertExpression(whileStmt.getCondition());
                var parts = extractLoopParts(whileStmt.getBody());
                return emitLoop(cond, parts.invariants(), parts.body(), null, List.of(), breakLbl, continueLbl);
            });
            case ForStmt forLoop -> {
                if (forLoop.getInitialization().size() > 1 || forLoop.getUpdate().size() > 1) {
                    throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                }
                yield withLoopLabels(forLoop.getBody(), (breakLbl, continueLbl) -> {
                    StmtExpr init = forLoop.getInitialization().isEmpty()
                            ? new StmtExpr.Block(List.of(), Optional.empty())
                            : convertExpression(forLoop.getInitialization().get(0));
                    StmtExpr cond = forLoop.getCompare().isPresent()
                            ? convertExpression(forLoop.getCompare().get())
                            : new StmtExpr.LiteralBool(true);
                    StmtExpr step = forLoop.getUpdate().isEmpty()
                            ? new StmtExpr.Block(List.of(), Optional.empty())
                            : convertExpression(forLoop.getUpdate().get(0));
                    var parts = extractLoopParts(forLoop.getBody());
                    return emitLoop(cond, parts.invariants(), parts.body(), step, List.of(init), breakLbl, continueLbl);
                });
            }
            case DoStmt doWhile -> {
                var breakLbl = freshLabel("loop_break");
                var continueLbl = freshLabel("loop_continue");
                var javaLabel = pendingLabel;
                pendingLabel = null;
                labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
                try {
                    var cond = convertExpression(doWhile.getCondition());
                    var parts = extractLoopParts(doWhile.getBody());
                    var notCond = primitiveOp(new Operation.Not(), node(cond));
                    var exitIfDone = new StmtExpr.IfThenElse(node(notCond),
                            node(new StmtExpr.Exit(breakLbl)), Optional.empty());
                    var wrappedBody = new StmtExpr.Block(List.of(node(parts.body())), Optional.of(continueLbl));
                    var whileBody = new StmtExpr.Block(List.of(node(wrappedBody), node(exitIfDone)), Optional.empty());
                    var whileNode = new StmtExpr.While(node(new StmtExpr.LiteralBool(true)),
                            parts.invariants(), Optional.empty(), node(whileBody), false);
                    yield new StmtExpr.Block(List.of(node(whileNode)), Optional.of(breakLbl));
                } finally {
                    labelStack.pop();
                }
            }
            case BreakStmt breakStmt -> new StmtExpr.Exit(resolveBreakLabel(breakStmt));
            case ContinueStmt contStmt -> new StmtExpr.Exit(resolveContinueLabel(contStmt));
            case LabeledStmt labeledStmt -> {
                pendingLabel = labeledStmt.getLabel().asString();
                var inner = labeledStmt.getStatement();
                if (inner instanceof WhileStmt || inner instanceof ForStmt || inner instanceof DoStmt) {
                    yield convertNestedStatement(inner);
                } else {
                    String brkLbl = freshLabel("label_break");
                    labelStack.push(new LabelEntry(pendingLabel, brkLbl, null));
                    pendingLabel = null;
                    try {
                        StmtExpr body = convertNestedStatementOrEmpty(inner);
                        yield new StmtExpr.Block(List.of(node(body)), Optional.of(brkLbl));
                    } finally {
                        labelStack.pop();
                    }
                }
            }
            case EmptyStmt ignored -> null;
            case com.github.javaparser.ast.stmt.SwitchStmt sw -> convertSwitch(sw.getSelector(), sw.getEntries(), false);
            case com.github.javaparser.ast.stmt.TryStmt tryStmt -> {
                // The decompiler never populates resources (try-with-resources
                // arrives pre-desugared as nested try/finally with a
                // synthesized Throwable re-throw, which then answers to the
                // declared-throws rule below -- same net refusal as JFE).
                if (!tryStmt.getResources().isEmpty()) {
                    throw new JavaViolationException(
                            "try-with-resources is not supported; use try/finally");
                }
                // The arms' catch types license `throw`s inside the try BODY
                // only (a throw in an arm or finalizer answers to the next
                // try out, as in Java), so scope the push to the body.
                List<String> armTypes = new ArrayList<>();
                for (var catcher : tryStmt.getCatchClauses()) {
                    armTypes.addAll(caughtClassesOf(catcher));
                }
                StmtExpr tryBody;
                enclosingCatchTypes.push(armTypes);
                try {
                    tryBody = convertNestedStatementOrEmpty(tryStmt.getTryBlock());
                } finally {
                    enclosingCatchTypes.pop();
                }
                List<org.strata.jverify.laurel.CatchClause> catches = new ArrayList<>();
                for (var catcher : tryStmt.getCatchClauses()) {
                    catches.add(convertCatcher(catcher));
                }
                Optional<AstNode<StmtExpr>> finallyArm = tryStmt.getFinallyBlock().isPresent()
                        ? Optional.of(node(convertNestedStatementOrEmpty(tryStmt.getFinallyBlock().get()),
                                statementRange(tryStmt.getFinallyBlock().get())))
                        : Optional.empty();
                yield new StmtExpr.Try(node(tryBody, statementRange(tryStmt.getTryBlock())),
                        catches, finallyArm);
            }
            case com.github.javaparser.ast.stmt.ThrowStmt throwStmt -> {
                String thrownFqn = checkThrowCoverage(throwStmt);
                // Link the whole chain: the Laurel `throw` must type-check
                // against the declared join, and any supertype a caller (or
                // local arm) may catch needs that guard's `is` test related.
                registerHierarchyUpToThrowable(thrownFqn);
                yield new StmtExpr.Throw(node(convertExpression(throwStmt.getExpression())));
            }
            default -> throw new JavaViolationException(
                    "Unsupported statement: " + statement.getClass().getSimpleName());
        };
    }

    /**
     * A `throw` needs somewhere to land: an enclosing catch arm that covers
     * its type, or the method's declared exceptional exit. Java lets an
     * unchecked throw go undeclared and uncaught; JVerify does not (JFE's
     * declared-throws discipline). Returns the thrown static type's fqn.
     */
    private String checkThrowCoverage(com.github.javaparser.ast.stmt.ThrowStmt throwStmt) {
        String thrownFqn = thrownStaticTypeFqn(throwStmt.getExpression());
        if (thrownFqn == null || !isThrowableSubtype(thrownFqn)) {
            throw new JavaViolationException(
                    "Unsupported thrown expression type: " + throwStmt.getExpression());
        }
        boolean caughtLocally = enclosingCatchTypes.stream().anyMatch(arms ->
                arms.stream().anyMatch(c -> isSubclassOf(thrownFqn, c)));
        if (caughtLocally) {
            return thrownFqn;
        }
        if (currentThrowsJoin == null) {
            throw new JavaViolationException(
                    "throw requires the enclosing method to declare the thrown type"
                            + " in its throws clause");
        }
        if (!isSubclassOf(thrownFqn, currentThrowsJoin)) {
            throw new JavaViolationException(
                    "thrown type '" + thrownFqn
                            + "' is not covered by the declared throws clause ('"
                            + currentThrowsJoin + "')");
        }
        return thrownFqn;
    }

    /**
     * Check every reachable throw's coverage BEFORE any statement converts.
     * The decompiled try-with-resources handler places the generated close()
     * machinery before the re-throw, so conversion order would surface the
     * close() call's refusal instead of the declared-throws diagnostic JFE
     * reports. Lambdas are skipped (spec properties, no runtime throw); try
     * scoping mirrors the conversion-time push/pop.
     */
    private void prescanThrowCoverage(com.github.javaparser.ast.Node node) {
        if (node instanceof com.github.javaparser.ast.expr.LambdaExpr) {
            return;
        }
        if (node instanceof com.github.javaparser.ast.stmt.TryStmt tryStmt) {
            List<String> armTypes = new ArrayList<>();
            for (var catcher : tryStmt.getCatchClauses()) {
                armTypes.addAll(caughtClassesOf(catcher));
            }
            enclosingCatchTypes.push(armTypes);
            try {
                prescanThrowCoverage(tryStmt.getTryBlock());
            } finally {
                enclosingCatchTypes.pop();
            }
            for (var catcher : tryStmt.getCatchClauses()) {
                prescanThrowCoverage(catcher.getBody());
            }
            tryStmt.getFinallyBlock().ifPresent(this::prescanThrowCoverage);
            return;
        }
        if (node instanceof com.github.javaparser.ast.stmt.ThrowStmt throwStmt) {
            checkThrowCoverage(throwStmt);
            return;
        }
        for (var child : node.getChildNodes()) {
            prescanThrowCoverage(child);
        }
    }

    /** The classes a catch arm's parameter covers (several for a multi-catch). */
    private List<String> caughtClassesOf(com.github.javaparser.ast.stmt.CatchClause catcher) {
        var type = catcher.getParameter().getType();
        List<Type> alternatives = type instanceof com.github.javaparser.ast.type.UnionType union
                ? new ArrayList<>(union.getElements())
                : List.of(type);
        List<String> caught = new ArrayList<>();
        for (var alt : alternatives) {
            if (!(alt instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot)) {
                throw new JavaViolationException("Unsupported catch type: " + alt);
            }
            String fqn = resolveClassFqn(cot.getNameWithScope());
            if (!isThrowableSubtype(fqn)) {
                throw new JavaViolationException("Unsupported catch type: " + alt);
            }
            caught.add(fqn);
        }
        return caught;
    }

    /**
     * A Java {@code catch (E e)} arm as a Laurel CatchClause: the binding
     * keeps its Java name, type dispatch becomes the guard {@code e is E} (a
     * multi-catch becomes the disjunction of type tests), and arms stay in
     * source order because Laurel tries clauses first-match-wins exactly as
     * Java does. The bindingType is left Unknown: Strata's resolution
     * computes it as the LCA of the exception types thrown in the try body
     * (JFE's convertCatcher).
     */
    private org.strata.jverify.laurel.CatchClause convertCatcher(
            com.github.javaparser.ast.stmt.CatchClause catcher) {
        String binding = catcher.getParameter().getNameAsString();
        StmtExpr predicate = null;
        for (String fqn : caughtClassesOf(catcher)) {
            registerHierarchyUpToThrowable(fqn);
            StmtExpr test = new StmtExpr.IsType(
                    node(var_(binding)),
                    node(new HighType.UserDefined(ident(fqn))));
            predicate = predicate == null
                    ? test
                    : primitiveOp(new Operation.Or(), node(predicate), node(test));
        }
        StmtExpr body = convertNestedStatementOrEmpty(catcher.getBody());
        return new org.strata.jverify.laurel.CatchClause(ident(binding),
                Optional.of(node(predicate)),
                node(body, statementRange(catcher.getBody())),
                node(new HighType.Unknown()));
    }

    /**
     * The static type (fqn) of a thrown expression: a `new E(...)`, a local /
     * parameter / catch binding named at a class type, or a cast. Null when
     * unresolvable.
     */
    private String thrownStaticTypeFqn(Expression expr) {
        expr = skipParens(expr);
        if (expr instanceof com.github.javaparser.ast.expr.ObjectCreationExpr newExpr) {
            return resolveClassFqn(newExpr.getType().getNameWithScope());
        }
        if (expr instanceof CastExpr cast
                && cast.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot) {
            return resolveClassFqn(cot.getNameWithScope());
        }
        if (expr instanceof NameExpr name) {
            Type declared = declaredTypeOf(name);
            if (declared instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot) {
                return resolveClassFqn(cot.getNameWithScope());
            }
        }
        return null;
    }

    /**
     * True when {@code expr}'s declared type is JVerify's ThrowsOnCase --
     * a captured/detached exceptional-case handle (local, parameter, or a
     * field of a compiled class).
     */
    private boolean isThrowsOnCaseTyped(Expression expr) {
        expr = skipParens(expr);
        Type declared = null;
        if (expr instanceof NameExpr name) {
            declared = declaredTypeOf(name);
            if (declared == null) {
                // A field of a compiled class (e.g. a static final handle).
                declared = fieldTypeByName(name.getNameAsString());
            }
        } else if (expr instanceof com.github.javaparser.ast.expr.FieldAccessExpr fa) {
            // The decompiler qualifies a static field read: Owner.DETACHED.
            declared = fieldTypeByName(fa.getNameAsString());
        }
        return declared instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot
                && cot.getNameAsString().equals("ThrowsOnCase");
    }

    /**
     * The fqn of an instance-call receiver's class when it is a LIBRARY class
     * (outside this compilation), or null. A string literal receiver is
     * {@code java.lang.String}; otherwise the receiver's declared static type
     * decides. Program classes return null (their calls resolve normally).
     */
    private String libraryReceiverClass(Expression receiver) {
        receiver = skipParens(receiver);
        if (receiver instanceof com.github.javaparser.ast.expr.StringLiteralExpr) {
            return "java.lang.String";
        }
        String fqn = staticTypeFqn(receiver);
        if (fqn == null || sourceTypes.containsKey(fqn) || knownClasses.containsKey(fqn)) {
            return null;
        }
        // Resolvable off the verifier's own classpath = a library class.
        try {
            Class.forName(fqn, false, getClass().getClassLoader());
            return fqn;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * A 1-character range at the first occurrence of {@code needle} within
     * the current statement's source text -- how JFE's javac diagnostics
     * anchor at a sub-expression's own position, which the CRT's
     * statement-level spans cannot provide directly. Falls back to the
     * statement range when the text is not found.
     */
    private Range pointRangeAt(String needle) {
        if (currentSource == null || currentIndex == null
                || currentStatementRange == UNKNOWN_RANGE) {
            return Reporter.NO_RANGE;
        }
        int start = (int) currentStatementRange.range().start().byteIdx();
        int end = (int) currentStatementRange.range().stop().byteIdx();
        int at = currentSource.indexOf(needle, start);
        if (at < 0 || at >= end) {
            return positionRange(currentStatementRange);
        }
        var pos = currentIndex.position(at);
        return new Range(pos, new org.strata.jverify.common.Position(pos.line(), pos.character() + 1));
    }

    /** The declared type of a uniquely-named field of any compiled class, or null. */
    private Type fieldTypeByName(String fieldName) {
        return declaredFields.values().stream()
                .map(fields -> fields.get(fieldName))
                .filter(java.util.Objects::nonNull)
                .map(FieldInfo::type)
                .findFirst().orElse(null);
    }

    /**
     * The declared type of a simple name: a local declarator, a method
     * parameter, a catch binding, or a one-parameter lambda parameter in
     * scope. Extends staticTypeFqn's local/param lookup with the
     * exception-relevant binders.
     */
    private Type declaredTypeOf(NameExpr name) {
        String text = name.getNameAsString();
        for (var catcher = name.findAncestor(com.github.javaparser.ast.stmt.CatchClause.class);
                catcher.isPresent();
                catcher = catcher.get().findAncestor(com.github.javaparser.ast.stmt.CatchClause.class)) {
            if (catcher.get().getParameter().getNameAsString().equals(text)) {
                return catcher.get().getParameter().getType();
            }
        }
        var lambda = name.findAncestor(com.github.javaparser.ast.expr.LambdaExpr.class);
        if (lambda.isPresent()) {
            for (var p : lambda.get().getParameters()) {
                if (p.getNameAsString().equals(text)) {
                    return p.getType();
                }
            }
        }
        var method = name.findAncestor(MethodDeclaration.class);
        var decl = method.flatMap(m -> m.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(text))
                .findFirst());
        if (decl.isPresent()) {
            return decl.get().getType();
        }
        return method.flatMap(m -> m.getParameters().stream()
                        .filter(p -> p.getNameAsString().equals(text))
                        .findFirst())
                .map(Parameter::getType).orElse(null);
    }

    // --- Switch desugaring (port of JFE's SwitchDesugarer: arrow switches -> if cascade) ---

    private int switchSelCounter = 0;

    /**
     * Desugar an arrow-form switch (statement or expression) to an
     * if-then-else cascade, mirroring JFE's SwitchDesugarer:
     * - the default arm is the FINAL else regardless of its source position
     *   (default only fires when no case matches)
     * - multi-label cases fold with short-circuit or
     * - a non-trivial selector is hoisted into a synthetic local so it is
     *   evaluated exactly once (Laurel blocks yield their last value, so the
     *   same shape works in expression position)
     */
    private StmtExpr convertSwitch(Expression selector,
                                   List<com.github.javaparser.ast.stmt.SwitchEntry> entries,
                                   boolean asExpression) {
        boolean hoist = !(selector instanceof NameExpr
                || selector instanceof IntegerLiteralExpr
                || selector instanceof CharLiteralExpr);
        String selName = null;
        StmtExpr selValue = null;
        if (hoist) {
            selName = "$sel" + (switchSelCounter++);
            selValue = convertExpression(selector);
        }

        var arms = normalizeSwitchEntries(entries);
        SwitchArm defaultArm = null;
        var cases = new ArrayList<SwitchArm>();
        for (var arm : arms) {
            if (arm.isDefault()) {
                defaultArm = arm;
            } else {
                cases.add(arm);
            }
        }

        StmtExpr cascade = defaultArm != null
                ? convertSwitchArm(defaultArm, asExpression)
                : null;
        for (int i = cases.size() - 1; i >= 0; i--) {
            var arm = cases.get(i);
            StmtExpr cond = null;
            for (var label : arm.labels()) {
                StmtExpr sel = hoist ? var_(selName) : convertExpression(selector);
                StmtExpr eq = primitiveOp(new Operation.Eq(), node(sel), node(convertExpression(label)));
                cond = cond == null ? eq : primitiveOp(new Operation.OrElse(), node(cond), node(eq));
            }
            cascade = new StmtExpr.IfThenElse(node(cond),
                    node(convertSwitchArm(arm, asExpression)),
                    cascade != null ? Optional.of(node(cascade)) : Optional.empty());
        }
        if (cascade == null) {
            cascade = new StmtExpr.Block(List.of(), Optional.empty());
        }
        if (!hoist) {
            return cascade;
        }
        var typeNode = node((HighType) new HighType.UserDefined(ident("int32")));
        var declared = new Variable.Declare(
                new ParameterOpt(ident(selName), Optional.of(typeNode)));
        var bind = new StmtExpr.Assign(List.of(nodeVar(declared)), node(selValue));
        return new StmtExpr.Block(List.of(node(bind), node(cascade)), Optional.empty());
    }

    private record SwitchArm(List<Expression> labels, boolean isDefault, List<Statement> statements) {
    }

    /**
     * Normalize switch entries into complete arms. Arrow entries map 1:1;
     * colon-form groups (what the decompiler recovers, since bytecode keeps
     * no arrow/colon distinction) merge empty label-only groups into the
     * next group and strip the trailing {@code break;}. Real fall-through
     * (a non-terminated group followed by more groups) is unsupported.
     */
    private List<SwitchArm> normalizeSwitchEntries(List<com.github.javaparser.ast.stmt.SwitchEntry> entries) {
        var arms = new ArrayList<SwitchArm>();
        var pendingLabels = new ArrayList<Expression>();
        boolean pendingDefault = false;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            pendingLabels.addAll(entry.getLabels());
            pendingDefault |= entry.getLabels().isEmpty();
            var statements = new ArrayList<Statement>(entry.getStatements());
            if (entry.getType() == com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP) {
                if (statements.isEmpty()) {
                    continue; // label-only group: merges with the next one
                }
                if (!statements.isEmpty() && statements.getLast() instanceof BreakStmt brk
                        && brk.getLabel().isEmpty()) {
                    statements.removeLast();
                } else if (i < entries.size() - 1
                        && !(statements.getLast() instanceof ReturnStmt)
                        && !(statements.getLast() instanceof com.github.javaparser.ast.stmt.ThrowStmt)) {
                    throw new JavaViolationException("Fall-through in switch is not supported");
                }
            }
            arms.add(new SwitchArm(new ArrayList<>(pendingLabels), pendingDefault, statements));
            pendingLabels.clear();
            pendingDefault = false;
        }
        if (!pendingLabels.isEmpty() || pendingDefault) {
            arms.add(new SwitchArm(new ArrayList<>(pendingLabels), pendingDefault, List.of()));
        }
        return arms;
    }

    /** One switch arm: the value expression (expression form) or the statements (statement form). */
    private StmtExpr convertSwitchArm(SwitchArm arm, boolean asExpression) {
        if (asExpression) {
            if (arm.statements().size() == 1
                    && arm.statements().getFirst() instanceof ExpressionStmt es) {
                return convertExpression(es.getExpression());
            }
            throw new JavaViolationException("Unsupported switch-expression arm (only single expressions)");
        }
        List<AstNode<StmtExpr>> stmts = new ArrayList<>();
        for (var s : arm.statements()) {
            StmtExpr converted = convertNestedStatement(s);
            if (converted != null) {
                stmts.add(node(converted, statementRange(s)));
            }
        }
        return stmts.size() == 1 ? stmts.getFirst().val() : new StmtExpr.Block(stmts, Optional.empty());
    }

    /** Convert a nested statement, updating the current default range to its span. */
    private StmtExpr convertNestedStatement(Statement statement) {
        var saved = currentStatementRange;
        currentStatementRange = statementRange(statement);
        try {
            return convertStatement(statement);
        } finally {
            currentStatementRange = saved;
        }
    }

    private StmtExpr convertNestedStatementOrEmpty(Statement statement) {
        StmtExpr converted = convertNestedStatement(statement);
        return converted != null ? converted : new StmtExpr.Block(List.of(), Optional.empty());
    }

    private StmtExpr convertExpressionStatement(ExpressionStmt exprStmt) {
        String jverifyName = jverifyCallName(exprStmt.getExpression());
        if (jverifyName != null && isHeaderContractName(jverifyName)) {
            throw new JavaViolationException(
                    "call to contract method must come before the rest of the body");
        }
        return convertExpression(exprStmt.getExpression());
    }

    private StmtExpr convertBlock(BlockStmt blk) {
        List<AstNode<StmtExpr>> statements = new ArrayList<>();
        for (var statement : blk.getStatements()) {
            var saved = currentStatementRange;
            currentStatementRange = statementRange(statement);
            try {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) {
                    statements.add(node(converted, statementRange(statement)));
                }
            } finally {
                currentStatementRange = saved;
            }
        }
        return new StmtExpr.Block(statements, Optional.empty());
    }

    // --- Loops (label scheme mirrors JFE's emitLoop) ---

    private record LoopParts(List<AstNode<StmtExpr>> invariants, StmtExpr body) {
    }

    private LoopParts extractLoopParts(Statement body) {
        if (!(body instanceof BlockStmt loopBlock)) {
            return new LoopParts(List.of(), convertNestedStatementOrEmpty(body));
        }
        var contract = extractContract(loopBlock.getStatements());
        if (!contract.preconditions().isEmpty() || !contract.postconditions().isEmpty()) {
            throw new JavaViolationException("preconditions are not allowed in a loop");
        }
        List<AstNode<StmtExpr>> invariants = new ArrayList<>();
        for (var inv : contract.invariants()) {
            var argRange = argumentRange(statementRange(inv));
            var saved = currentStatementRange;
            currentStatementRange = argRange;
            try {
                invariants.add(node(
                        inContract(() -> convertExpression(specArgument(inv, "invariant"))), argRange));
            } finally {
                currentStatementRange = saved;
            }
        }
        List<AstNode<StmtExpr>> stmts = new ArrayList<>();
        for (var s : contract.implementation()) {
            var saved = currentStatementRange;
            // JFE stamps loop-body statements with the statement itself
            // (semicolon included), unlike method-level statements.
            currentStatementRange = statementRangeFull(s);
            try {
                StmtExpr converted = convertStatement(s);
                if (converted != null) {
                    stmts.add(node(converted, statementRangeFull(s)));
                }
            } finally {
                currentStatementRange = saved;
            }
        }
        return new LoopParts(invariants, new StmtExpr.Block(stmts, Optional.empty()));
    }

    private StmtExpr withLoopLabels(Statement loopBody,
                                    java.util.function.BiFunction<String, String, StmtExpr> body) {
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

    private StmtExpr emitLoop(StmtExpr cond, List<AstNode<StmtExpr>> invariants, StmtExpr loopBody,
                              StmtExpr step, List<StmtExpr> preamble,
                              String breakLbl, String continueLbl) {
        if (breakLbl != null) {
            StmtExpr wrappedBody = new StmtExpr.Block(List.of(node(loopBody)), Optional.of(continueLbl));
            StmtExpr whileBody = step != null
                    ? new StmtExpr.Block(List.of(node(wrappedBody), node(step)), Optional.empty())
                    : wrappedBody;
            StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, Optional.empty(), node(whileBody), false);
            List<AstNode<StmtExpr>> outerStmts = new ArrayList<>();
            for (var p : preamble) {
                outerStmts.add(node(p));
            }
            outerStmts.add(node(whileNode));
            return new StmtExpr.Block(outerStmts, Optional.of(breakLbl));
        }
        StmtExpr whileBody = step != null
                ? new StmtExpr.Block(List.of(node(loopBody), node(step)), Optional.empty())
                : loopBody;
        StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, Optional.empty(), node(whileBody), false);
        if (preamble.isEmpty()) {
            return whileNode;
        }
        List<AstNode<StmtExpr>> stmts = new ArrayList<>();
        for (var p : preamble) {
            stmts.add(node(p));
        }
        stmts.add(node(whileNode));
        return new StmtExpr.Block(stmts, Optional.empty());
    }

    private String resolveBreakLabel(BreakStmt brk) {
        if (brk.getLabel().isPresent()) {
            String target = brk.getLabel().get().asString();
            for (var entry : labelStack) {
                if (target.equals(entry.javaLabel())) {
                    return entry.breakLabel();
                }
            }
        } else {
            for (var entry : labelStack) {
                if (entry.breakLabel() != null) {
                    return entry.breakLabel();
                }
            }
        }
        throw new JavaViolationException("break target not found");
    }

    private String resolveContinueLabel(ContinueStmt cont) {
        if (cont.getLabel().isPresent()) {
            String target = cont.getLabel().get().asString();
            for (var entry : labelStack) {
                if (target.equals(entry.javaLabel()) && entry.continueLabel() != null) {
                    return entry.continueLabel();
                }
            }
        } else {
            for (var entry : labelStack) {
                if (entry.continueLabel() != null) {
                    return entry.continueLabel();
                }
            }
        }
        throw new JavaViolationException("continue target not found");
    }

    // --- Expressions ---

    /**
     * A static field (the decompiler owner-qualifies it: {@code C.counter})
     * has no receiver object, so it cannot lower to the composite heap model;
     * emitting it as an instance field dies later in Strata resolution.
     * Refused up front instead. (Compile-time constants never reach here --
     * javac inlines them into the bytecode.)
     */
    private void refuseStaticFieldAccess(com.github.javaparser.ast.expr.FieldAccessExpr fa) {
        if (skipParens(fa.getScope()) instanceof NameExpr owner
                && !owner.getNameAsString().equals("this")
                && declaredTypeOf(owner) == null
                && knownClasses.containsKey(owner.getNameAsString())) {
            throw new JavaViolationException("static field access is not yet supported");
        }
    }

    private Variable convertVariable(Expression expr) {
        return switch (expr) {
            case NameExpr name -> new Variable.Local(
                    ident(renames.getOrDefault(name.getNameAsString(), name.getNameAsString())));
            case com.github.javaparser.ast.expr.FieldAccessExpr fa -> {
                refuseStaticFieldAccess(fa);
                ensureFieldOnComposite(fa);
                yield new Variable.Field(node(convertExpression(fa.getScope())),
                        ident(fa.getNameAsString()));
            }
            case EnclosedExpr enc -> convertVariable(enc.getInner());  // 3.26: non-optional
            default -> throw new JavaViolationException(
                    "Unsupported variable expression: " + expr.getClass().getSimpleName());
        };
    }

    /**
     * Declare the accessed field on its owning composite so Strata's heap
     * parameterization can resolve {@code owner.field} (JFE's
     * ensureFieldOnComposite; only the accessed field is emitted). The owner
     * is the receiver's static type, falling back to a unique declaring
     * class when the receiver type is unknown.
     */
    private void ensureFieldOnComposite(com.github.javaparser.ast.expr.FieldAccessExpr fa) {
        String ownerFqn = staticTypeFqn(fa.getScope());
        String fieldName = fa.getNameAsString();
        if (ownerFqn == null || !hasField(ownerFqn, fieldName)) {
            ownerFqn = declaredFields.entrySet().stream()
                    .filter(e -> e.getValue().containsKey(fieldName))
                    .map(Map.Entry::getKey)
                    .reduce((a, b) -> a) // first declaring class
                    .orElse(null);
        }
        if (ownerFqn == null) {
            throw new JavaViolationException("Unsupported field access: " + fa);
        }
        FieldInfo info = declaredFields.get(ownerFqn).get(fieldName);
        referencedCompositeTypes.add(ownerFqn);
        var fields = compositeFields.computeIfAbsent(ownerFqn, k -> new LinkedHashMap<>());
        if (!fields.containsKey(fieldName)) {
            fields.put(fieldName, new org.strata.jverify.laurel.Field(
                    ident(fieldName), !info.isFinal(), node(translateType(info.type())),
                    /*initializer=*/Optional.empty()));
        }
    }

    private boolean hasField(String fqn, String fieldName) {
        var fields = declaredFields.get(fqn);
        return fields != null && fields.containsKey(fieldName);
    }

    /** The fully qualified static type of an expression, or null when unknown. */
    private String staticTypeFqn(Expression expr) {
        expr = skipParens(expr);
        // The receiver: its static type is the enclosing class.
        if (expr instanceof com.github.javaparser.ast.expr.ThisExpr
                || (expr instanceof NameExpr n && n.getNameAsString().equals("this"))) {
            return currentClassFqn;
        }
        if (expr instanceof NameExpr name) {
            Type type = declaredTypeOf(name);
            if (type instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cls) {
                return qualifyTypeName(cls.getNameWithScope());
            }
        }
        // A cast scope: `((ParseError) e).code` reads a field of the cast's
        // target type (the per-type-claim idiom in exceptional postconditions).
        if (expr instanceof CastExpr cast
                && cast.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot) {
            return qualifyTypeName(cot.getNameWithScope());
        }
        if (expr instanceof com.github.javaparser.ast.expr.ObjectCreationExpr newExpr) {
            return qualifyTypeName(newExpr.getType().getNameWithScope());
        }
        return null;
    }

    private StmtExpr convertExpression(Expression expr) {
        return switch (expr) {
            case IntegerLiteralExpr lit -> longLiteral(lit.asNumber().longValue());
            case LongLiteralExpr lit -> longLiteral(lit.asNumber().longValue());
            case BooleanLiteralExpr lit -> new StmtExpr.LiteralBool(lit.getValue());
            case CharLiteralExpr lit -> longLiteral(lit.asChar());
            case NameExpr name -> {
                String text = name.getNameAsString();
                // Decompiler prints Integer/Long.MIN_VALUE as a NameExpr
                // ("-2147483648") because the bare literal is not valid Java
                // source. Recognize the numeric shape and lower it as a value.
                if (text.matches("-\\d+L?")) {
                    yield longLiteral(Long.parseLong(text.endsWith("L")
                            ? text.substring(0, text.length() - 1) : text));
                }
                // The receiver: slot 0 of an instance method, rendered `this`
                // by the decompiler's local table. Lowers to the synthesized
                // self? parameter (JFE's LAUREL_SELF_BINDING).
                if (text.equals("this")) {
                    yield var_(LAUREL_SELF_BINDING);
                }
                yield var_(renames.getOrDefault(text, text));
            }
            case com.github.javaparser.ast.expr.ThisExpr t -> var_(LAUREL_SELF_BINDING);
            case EnclosedExpr enc -> convertExpression(enc.getInner());
            case BinaryExpr binary -> convertBinary(binary);
            case UnaryExpr unary -> convertUnary(unary);
            // `e instanceof E` on exception types becomes Laurel's `is` type
            // test; anything else stays refused (JFE supports instanceof only
            // for Throwable subtypes, without binding patterns).
            case com.github.javaparser.ast.expr.InstanceOfExpr io -> {
                String fqn = io.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot
                        ? resolveClassFqn(cot.getNameWithScope())
                        : null;
                if (io.getPattern().isPresent() || fqn == null || !isThrowableSubtype(fqn)) {
                    throw new JavaViolationException(
                            "instanceof on opaque reference types is not yet supported");
                }
                registerHierarchyUpToThrowable(fqn);
                yield new StmtExpr.IsType(node(convertExpression(io.getExpression())),
                        node(new HighType.UserDefined(ident(fqn))));
            }
            case AssignExpr asgn -> convertAssign(asgn);
            case VariableDeclarationExpr decl -> convertVariableDeclaration(decl);
            case ConditionalExpr cond -> new StmtExpr.IfThenElse(
                    node(convertExpression(cond.getCondition())),
                    node(convertExpression(cond.getThenExpr())),
                    Optional.of(node(convertExpression(cond.getElseExpr()))));
            case CastExpr cast -> convertTypeCast(cast);
            case com.github.javaparser.ast.expr.SwitchExpr sw ->
                    convertSwitch(sw.getSelector(), sw.getEntries(), true);
            case MethodCallExpr call -> convertCall(call);
            // Instance-field read: Strata's heap parameterization rewrites
            // this into a readField call on the owning composite (JFE scheme).
            case com.github.javaparser.ast.expr.FieldAccessExpr fa -> {
                refuseStaticFieldAccess(fa);
                ensureFieldOnComposite(fa);
                yield new StmtExpr.Var(new Variable.Field(
                        node(convertExpression(fa.getScope())), ident(fa.getNameAsString())));
            }
            case com.github.javaparser.ast.expr.ObjectCreationExpr newExpr -> {
                if (inContractContext) {
                    // JFE's refuseAllocationInContract: a contract must not
                    // allocate (its meaning would depend on evaluation).
                    throw new JavaViolationException(
                            "object allocation (including lambdas) inside a contract is not yet supported");
                }
                String name = qualifyTypeName(newExpr.getType().getNameWithScope());
                referencedCompositeTypes.add(name);
                for (var arg : newExpr.getArguments()) {
                    convertExpression(arg); // JFE converts (and discards) ctor args
                }
                yield new StmtExpr.New(ident(name), /*typeArgs=*/List.of());
            }
            // A varargs call site materializes `new T[] { ... }` in bytecode;
            // JFE refuses the same shape with this message, anchored at the
            // array (here: its first element's source position, since the
            // array expression itself is synthesized by the decompiler).
            case com.github.javaparser.ast.expr.ArrayCreationExpr ace -> {
                if (ace.getInitializer().isPresent()
                        && !ace.getInitializer().get().getValues().isEmpty()) {
                    reporter.reportError(currentUri,
                            pointRangeAt(ace.getInitializer().get().getValues().get(0).toString()),
                            "new array with initializers is not supported");
                    throw new ReportedViolation();
                }
                throw new JavaViolationException(
                        "Unsupported expression: " + expr.getClass().getSimpleName());
            }
            default -> throw new JavaViolationException(
                    "Unsupported expression: " + javacNodeName(expr));
        };
    }

    /** Run a conversion with the contract-context flag set (allocation refusals). */
    private <T> T inContract(java.util.function.Supplier<T> conversion) {
        boolean saved = inContractContext;
        inContractContext = true;
        try {
            return conversion.get();
        } finally {
            inContractContext = saved;
        }
    }

    /**
     * The javac tree-node name JFE's refusal messages carry for this
     * javaparser node. JFE's unlambda pre-pass turns method references into
     * lambdas before the translator refuses them, so both map to JCLambda.
     */
    private static String javacNodeName(Expression expr) {
        return switch (expr) {
            case com.github.javaparser.ast.expr.LambdaExpr l -> "JCLambda";
            case com.github.javaparser.ast.expr.MethodReferenceExpr r -> "JCLambda";
            default -> expr.getClass().getSimpleName();
        };
    }

    private StmtExpr convertVariableDeclaration(VariableDeclarationExpr decl) {
        if (decl.getVariables().size() != 1) {
            throw new JavaViolationException("Multi-variable declarations are not supported");
        }
        VariableDeclarator v = decl.getVariable(0);
        // The decompiler prints `var` when the local has no LVT entry (an
        // unnamed `var _` binding): no type to translate, so declare the
        // Laurel local untyped and let inference type it from the initializer.
        boolean untyped = v.getType() instanceof com.github.javaparser.ast.type.VarType
                || (v.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType c
                        && c.getScope().isEmpty() && c.getNameAsString().equals("var"));
        var param = untyped
                ? new ParameterOpt(ident(v.getNameAsString()), Optional.empty())
                : new ParameterOpt(ident(v.getNameAsString()), Optional.of(node(translateType(v.getType()))));
        var declared = new Variable.Declare(param);
        if (v.getInitializer().isPresent()) {
            return new StmtExpr.Assign(List.of(nodeVar(declared)),
                    node(convertExpression(v.getInitializer().get())));
        }
        return new StmtExpr.Var(declared);
    }

    private StmtExpr convertAssign(AssignExpr asgn) {
        var target = convertVariable(asgn.getTarget());
        if (asgn.getOperator() == AssignExpr.Operator.ASSIGN) {
            return new StmtExpr.Assign(List.of(nodeVar(target)), node(convertExpression(asgn.getValue())));
        }
        Operation op = switch (asgn.getOperator()) {
            case PLUS -> new Operation.Add();
            case MINUS -> new Operation.Sub();
            case MULTIPLY -> new Operation.Mul();
            case DIVIDE -> new Operation.DivT();
            case REMAINDER -> new Operation.ModT();
            default -> throw new JavaViolationException(
                    "Unsupported compound assignment: " + asgn.getOperator());
        };
        return new StmtExpr.CompoundAssign(op, nodeVar(target), node(convertExpression(asgn.getValue())));
    }

    private StmtExpr convertBinary(BinaryExpr binary) {
        var lhs = node(convertExpression(binary.getLeft()));
        var rhs = node(convertExpression(binary.getRight()));
        Operation op = switch (binary.getOperator()) {
            case PLUS -> new Operation.Add();
            case MINUS -> new Operation.Sub();
            case MULTIPLY -> new Operation.Mul();
            case DIVIDE -> new Operation.DivT();
            case REMAINDER -> new Operation.ModT();
            case EQUALS -> new Operation.Eq();
            case NOT_EQUALS -> new Operation.Neq();
            case GREATER -> new Operation.Gt();
            case LESS -> new Operation.Lt();
            case GREATER_EQUALS -> new Operation.Geq();
            case LESS_EQUALS -> new Operation.Leq();
            case AND -> new Operation.AndThen();
            case OR -> new Operation.OrElse();
            default -> throw new JavaViolationException(
                    "Unsupported binary op: " + binary.getOperator());
        };
        return primitiveOp(op, lhs, rhs);
    }

    private StmtExpr convertUnary(UnaryExpr unary) {
        return switch (unary.getOperator()) {
            case LOGICAL_COMPLEMENT ->
                    primitiveOp(new Operation.Not(), node(convertExpression(unary.getExpression())));
            case MINUS -> {
                // `-5` decompiles as UnaryExpr(MINUS, 5); fold literals so the
                // encoding matches JFE's longLiteral (Neg wrapping of positives).
                if (unary.getExpression() instanceof IntegerLiteralExpr lit) {
                    yield longLiteral(-lit.asNumber().longValue());
                }
                if (unary.getExpression() instanceof LongLiteralExpr lit) {
                    yield longLiteral(-lit.asNumber().longValue());
                }
                yield primitiveOp(new Operation.Neg(), node(convertExpression(unary.getExpression())));
            }
            case PREFIX_INCREMENT -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Incr(),
                    nodeVar(convertVariable(unary.getExpression())));
            case PREFIX_DECREMENT -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Decr(),
                    nodeVar(convertVariable(unary.getExpression())));
            case POSTFIX_INCREMENT -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Incr(),
                    nodeVar(convertVariable(unary.getExpression())));
            case POSTFIX_DECREMENT -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Decr(),
                    nodeVar(convertVariable(unary.getExpression())));
            default -> throw new JavaViolationException("Unsupported unary op: " + unary.getOperator());
        };
    }

    // --- Casts (port of JFE's cast-helper scheme) ---

    private StmtExpr convertTypeCast(CastExpr cast) {
        // A cast to an exception type becomes Laurel's `as` (its embedded
        // type-test assertion is discharged by the instanceof antecedent in
        // the per-type-claim idiom `implies(e instanceof T, ((T) e)...)`).
        // Non-exception reference casts stay refused (JFE's scheme).
        if (cast.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType cot) {
            String fqn = resolveClassFqn(cot.getNameWithScope());
            if (isThrowableSubtype(fqn)) {
                registerHierarchyUpToThrowable(fqn);
                return new StmtExpr.AsType(node(convertExpression(cast.getExpression())),
                        node(new HighType.UserDefined(ident(fqn))));
            }
        }
        StmtExpr operand = convertExpression(cast.getExpression());
        HighType targetType;
        try {
            targetType = translateType(cast.getType());
        } catch (JavaViolationException e) {
            // JFE reports reference-type casts with the fully qualified
            // target: "Cast to <fqn> is not yet supported".
            throw new JavaViolationException(
                    "Cast to " + qualifyTypeName(cast.getType().asString()) + " is not yet supported");
        }
        HighType sourceType = inferCastSourceType(cast.getExpression());
        String targetName = laurelIntegerTypeName(targetType);
        if (targetName == null) {
            throw new JavaViolationException(
                    "Cast to " + qualifyTypeName(cast.getType().asString()) + " is not yet supported");
        }
        String sourceName = laurelTypeName(sourceType);
        String helperName = "$cast_to_" + targetName + "_from_" + sourceName;
        registerCastHelper(helperName, sourceName, sourceType, targetName, targetType);
        return new StmtExpr.StaticCall(ident(helperName), List.of(node(operand)));
    }

    /**
     * The static type of a cast operand, without javac attribution. The
     * decompiler works from typed bytecode, so the interesting cases are
     * narrow: a literal or arithmetic on int-typed locals is int32; a plain
     * name's declared type is found on its declarator when it is a local of
     * this method. Defaults to int32 — for the widening/narrowing helpers
     * only the target's bounds matter for soundness (the source name only
     * affects the helper's mangled name and parameter sort).
     */
    private HighType inferCastSourceType(Expression expr) {
        if (expr instanceof NameExpr name) {
            var decl = expr.findAncestor(MethodDeclaration.class)
                    .flatMap(m -> m.findAll(VariableDeclarator.class).stream()
                            .filter(v -> v.getNameAsString().equals(name.getNameAsString()))
                            .findFirst());
            if (decl.isPresent()) {
                return translateType(decl.get().getType());
            }
            var param = expr.findAncestor(MethodDeclaration.class)
                    .flatMap(m -> m.getParameters().stream()
                            .filter(p -> p.getNameAsString().equals(name.getNameAsString()))
                            .findFirst());
            if (param.isPresent()) {
                return translateType(param.get().getType());
            }
        }
        if (expr instanceof CastExpr cast) {
            return translateType(cast.getType());
        }
        if (expr instanceof LongLiteralExpr) {
            return new HighType.UserDefined(ident("int64"));
        }
        return new HighType.UserDefined(ident("int32"));
    }

    /** Fully qualify a decompiled type name against this compilation's classes (JFE prints fqns). */
    private String qualifyTypeName(String name) {
        if (name.indexOf('.') >= 0) {
            // Nested reference like Outer.Inner: qualify via the outer class.
            String outer = name.substring(0, name.indexOf('.'));
            String fqnOuter = knownClasses.get(outer);
            return fqnOuter != null ? fqnOuter + name.substring(name.indexOf('.')) : name;
        }
        return knownClasses.getOrDefault(name, name);
    }

    private String laurelIntegerTypeName(HighType targetType) {
        if (targetType instanceof HighType.UserDefined u && BOUNDS.get(u.name().text()) != null) {
            return u.name().text();
        }
        if (targetType instanceof HighType.TInt) {
            return "int";
        }
        return null;
    }

    private String laurelTypeName(HighType ty) {
        if (ty instanceof HighType.UserDefined u) {
            return u.name().text();
        }
        if (ty instanceof HighType.TInt) {
            return "int";
        }
        throw new JavaViolationException("Unsupported cast source type: " + ty);
    }

    private void registerCastHelper(String helperName, String sourceName, HighType sourceType,
                                    String targetName, HighType targetType) {
        if (castHelpers.containsKey(helperName)) {
            return;
        }
        var x = var_("x");
        Long[] bounds = BOUNDS.get(targetName);
        var castLabel = Optional.of("cast from " + sourceName + " to " + targetName);
        var preconditions = new ArrayList<Condition>();
        if (bounds != null) {
            if (bounds[0] != null) {
                preconditions.add(new Condition(
                        noRange(primitiveOp(new Operation.Geq(), noRange(x), noRange(longLiteral(bounds[0])))),
                        castLabel, new ConditionMode.Both()));
            }
            if (bounds[1] != null) {
                preconditions.add(new Condition(
                        noRange(primitiveOp(new Operation.Leq(), noRange(x), noRange(longLiteral(bounds[1])))),
                        castLabel, new ConditionMode.Both()));
            }
        }
        var resultEqX = primitiveOp(new Operation.Eq(), noRange(var_(LAUREL_RESULT_BINDING)), noRange(x));
        var postconditions = List.of(
                new Condition(noRange(resultEqX), Optional.empty(), new ConditionMode.Both()));
        var proc = new Procedure(
                ident(helperName),
                /*typeArgs=*/List.of(),
                List.of(new org.strata.jverify.laurel.Parameter(ident("x"), node(sourceType))),
                List.of(new org.strata.jverify.laurel.Parameter(ident(LAUREL_RESULT_BINDING), node(targetType))),
                preconditions,
                Optional.empty(),
                new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(List.of())),
                Optional.empty(), false, List.of(),
                Optional.empty(), Optional.empty(), List.of());
        castHelpers.put(helperName, proc);
    }

    // --- Calls ---

    private StmtExpr convertCall(MethodCallExpr call) {
        String jverifyName = jverifyCallName(call);
        if (jverifyName != null) {
            return convertJVerifyCall(call, jverifyName);
        }
        // A case clause on a handle that never chained off throwsOn in this
        // contract: refuse rather than misread it (JFE's detached-handle
        // check; the receiver's declared type identifies the clause).
        var scope = call.getScope();
        if (scope.isPresent() && isThrowsOnCaseTyped(scope.get())) {
            throw new JavaViolationException(
                    "a throwsOn case clause must chain directly off throwsOn(...); calling '"
                            + call.getNameAsString() + "' on a captured handle detaches it from its case");
        }
        // An instance call on a library value with no contract: JFE's
        // MissingContractCompiler warns at the receiver, then translation
        // proceeds and fails wherever it actually fails (e.g. an argument it
        // cannot translate), attributing each diagnostic to its own position.
        // Mirror that: warn, convert the pieces for their side effects and
        // refusals, then refuse the call itself (contract synthesis for
        // un-contracted callees is not ported).
        String receiverClass = scope.map(this::libraryReceiverClass).orElse(null);
        if (receiverClass != null) {
            reporter.reportWarning(currentUri, pointRangeAt(scope.get().toString()),
                    "missing contract for method '" + call.getNameAsString()
                            + "' in class '" + receiverClass + "'");
            for (var arg : call.getArguments()) {
                convertExpression(arg);
            }
            throw new JavaViolationException("Unsupported call: " + call);
        }
        // A chained receiver (e.g. `"s".formatted(...).length()`): convert the
        // receiver first so its own diagnostics (missing contract, refused
        // arguments) surface at their positions, depth-first as JFE does.
        if (scope.isPresent() && skipParens(scope.get()) instanceof MethodCallExpr chained) {
            convertCall(chained);
        }
        // Receiver-shape refusals first (JFE's receiverRefusal), so a super
        // call / freshly-allocated receiver names its own cause instead of
        // the general dispatch refusal.
        if (scope.isPresent()) {
            if (scope.get() instanceof com.github.javaparser.ast.expr.SuperExpr) {
                throw new JavaViolationException("super call is not yet supported");
            }
            if (skipParens(scope.get()) instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) {
                throw new JavaViolationException(
                        "method call on a freshly-allocated receiver is not yet supported");
            }
        }
        // An instance call on a program-class receiver: `this.m(...)` (how
        // the decompiler renders every implicit-this call) or an explicit
        // receiver expression. Distinguished from a static call by the scope
        // being a value, not a class name (a local shadowing a class name
        // resolves as the local, matching Java scoping).
        String bareName = call.getNameAsString();
        boolean scopeIsValue = scope.isPresent()
                && (skipParens(scope.get()) instanceof com.github.javaparser.ast.expr.ThisExpr
                        || (skipParens(scope.get()) instanceof NameExpr n
                                && (n.getNameAsString().equals("this") || declaredTypeOf(n) != null))
                        || skipParens(scope.get()) instanceof com.github.javaparser.ast.expr.FieldAccessExpr
                        || skipParens(scope.get()) instanceof CastExpr);
        if (scopeIsValue) {
            String receiverFqn = staticTypeFqn(skipParens(scope.get()));
            if (receiverFqn == null || !programClassModels.containsKey(receiverFqn)) {
                throw new JavaViolationException("call to '" + bareName
                        + "' on a receiver whose static type is not a class is not yet supported");
            }
            var callee = findCallee(receiverFqn, bareName);
            if (callee == null) {
                throw new JavaViolationException("call to '" + bareName
                        + "' on a receiver whose static type is not a class is not yet supported");
            }
            if (isOptedOut(programClassModels.get(callee.declaringFqn()), callee.method())) {
                throw new JavaViolationException(
                        "call to '" + bareName + "', which could not be translated");
            }
            enforceDispatchGate(receiverFqn, callee, bareName);
            enforceAncestryOverloadGate(receiverFqn, bareName);
            // A callee inherited from an ancestor resolves by name against
            // the receiver's composite, so Strata needs the extends chain
            // linked from the receiver's sort up to the declaring one (else
            // "Resolution failed: 'Receiver$callee' is not defined").
            if (!callee.declaringFqn().equals(receiverFqn)) {
                for (String fqn = receiverFqn;
                        fqn != null && !fqn.equals(callee.declaringFqn());
                        fqn = programSuper.get(fqn)) {
                    String parent = programSuper.get(fqn);
                    if (parent == null) {
                        break;
                    }
                    linkExtends(fqn, parent);
                }
            }
            if (currentMethodInfo != null) {
                currentMethodInfo.callees.add(callee.declaringFqn() + "#" + bareName);
            }
            var receiver = node(convertExpression(scope.get()));
            List<AstNode<StmtExpr>> args = new ArrayList<>();
            for (var arg : call.getArguments()) {
                args.add(node(convertExpression(arg)));
            }
            return new StmtExpr.InstanceCall(receiver, ident(bareName), args);
        }
        // A user static call: the decompiler owner-qualifies static calls, so
        // resolve the scope name against the classes of this compilation.
        if (scope.isEmpty() || !(scope.get() instanceof NameExpr owner)) {
            throw new JavaViolationException("Unsupported call: " + call);
        }
        String fqn = knownClasses.get(owner.getNameAsString());
        if (fqn == null) {
            throw new JavaViolationException(
                    "Call to unknown class '" + owner.getNameAsString() + "' is not supported");
        }
        // JFE applies overloadRefusal to static callees at each call site.
        refuseIfOverloaded(fqn, bareName, true);
        // A call to an opted-out method is refused: both overloads mangle to
        // the same Laurel name, so binding to the emitted sibling would be
        // wrong contract, wrong arity. Resolved arity-aware -- the emitted
        // same-name sibling must stay callable (CallToOptedOutOverload).
        var staticCm = programClassModels.get(fqn);
        if (staticCm != null) {
            for (var m : staticCm.methods()) {
                if (m.methodName().stringValue().equals(bareName)
                        && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0
                        && (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) != 0
                        && m.methodTypeSymbol().parameterCount() == call.getArguments().size()
                        && isOptedOut(staticCm, m)) {
                    throw new JavaViolationException(
                            "call to '" + bareName + "', which could not be translated");
                }
            }
        }
        if (currentMethodInfo != null) {
            currentMethodInfo.callees.add(fqn + "#" + bareName);
        }
        String calleeName = fqn + "_" + bareName;
        List<AstNode<StmtExpr>> args = new ArrayList<>();
        for (var arg : call.getArguments()) {
            args.add(node(convertExpression(arg)));
        }
        return new StmtExpr.StaticCall(ident(calleeName), args);
    }

    /** A located callee: the classfile method plus the class that declares it. */
    private record Callee(String declaringFqn, java.lang.classfile.MethodModel method) {
    }

    /** Lazily-parsed JCL classfile models (java.lang.Object etc.), null-capable. */
    private final Map<String, java.lang.classfile.ClassModel> jclClassModels = new HashMap<>();

    /**
     * The classfile model for {@code fqn}: this compilation's classes first,
     * then the runtime's own (read as a classloader resource, which the JRT
     * filesystem serves for JCL classes). Null when unresolvable.
     */
    private java.lang.classfile.ClassModel classModelFor(String fqn) {
        var cm = programClassModels.get(fqn);
        if (cm != null) {
            return cm;
        }
        if (jclClassModels.containsKey(fqn)) {
            return jclClassModels.get(fqn);
        }
        java.lang.classfile.ClassModel parsed = null;
        try (var in = ClassLoader.getSystemResourceAsStream(fqn.replace('.', '/') + ".class")) {
            if (in != null) {
                parsed = java.lang.classfile.ClassFile.of().parse(in.readAllBytes());
            }
        } catch (java.io.IOException e) {
            // fall through: cache the miss
        }
        jclClassModels.put(fqn, parsed);
        return parsed;
    }

    /**
     * Find the (non-synthetic) method {@code name} on {@code receiverFqn} or
     * its program-class ancestry, ancestor-nearest-first from the receiver.
     */
    private Callee findCallee(String receiverFqn, String name) {
        for (String fqn = receiverFqn; fqn != null; fqn = programSuper.get(fqn)) {
            var cm = programClassModels.get(fqn);
            if (cm == null) {
                break;
            }
            for (var m : cm.methods()) {
                if (m.methodName().stringValue().equals(name)
                        && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE
                                | java.lang.classfile.ClassFile.ACC_STATIC)) == 0) {
                    return new Callee(fqn, m);
                }
            }
        }
        return null;
    }

    /**
     * JFE's dispatchRefusal: an instance call is translatable only when
     * provably monomorphic -- the callee is final or private, or its
     * declaring class is final, or the receiver's static type is a final
     * class. Deliberately not keyed on whether the callee overrides
     * something: the unsound case is a non-overriding method overridden by a
     * subtype later.
     */
    private void enforceDispatchGate(String receiverFqn, Callee callee, String name) {
        int mflags = callee.method().flags().flagsMask();
        if ((mflags & (java.lang.classfile.ClassFile.ACC_FINAL
                | java.lang.classfile.ClassFile.ACC_PRIVATE)) != 0) {
            return;
        }
        var declaring = programClassModels.get(callee.declaringFqn());
        if (declaring != null
                && (declaring.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_FINAL) != 0) {
            return;
        }
        var receiver = programClassModels.get(receiverFqn);
        if (receiver != null
                && (receiver.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_FINAL) != 0) {
            return;
        }
        throw new JavaViolationException(
                "polymorphic dispatch through interface/superclass is not yet supported");
    }

    /**
     * JFE's ancestryOverloadRefusal: by-name resolution must be unambiguous
     * over the receiver's supertype closure. Counts distinct erased
     * parameter signatures of {@code name}, skipping synthetic/bridge members
     * and private members of strict supertypes (JLS 8.2: not members of the
     * receiver's type). More than one signature refuses; same-signature
     * overRIDES are fine.
     */
    private void enforceAncestryOverloadGate(String receiverFqn, String name) {
        var signatures = new java.util.LinkedHashSet<String>();
        boolean beyondReceiver = false;
        boolean anyBeyond = false;
        for (String fqn = receiverFqn; fqn != null; ) {
            var cm = classModelFor(fqn);
            if (cm == null) {
                break;
            }
            for (var m : cm.methods()) {
                if (!m.methodName().stringValue().equals(name)
                        || (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE
                                | java.lang.classfile.ClassFile.ACC_STATIC)) != 0) {
                    continue;
                }
                if (beyondReceiver
                        && (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_PRIVATE) != 0) {
                    continue;
                }
                if (isOptedOut(cm, m)) {
                    continue; // emits no procedure, so it cannot collide
                }
                if (signatures.add(m.methodTypeSymbol().parameterList().toString()) && beyondReceiver) {
                    anyBeyond = true;
                }
            }
            beyondReceiver = true;
            // Walk the full closure, JCL included (JFE uses javacTypes
            // closure: Object.toString() collides with a user toString(x)).
            fqn = cm.superclass()
                    .map(sup -> sup.asInternalName().replace('/', '.').replace('$', '.'))
                    .orElse(null);
        }
        if (signatures.size() > 1) {
            String where = anyBeyond
                    ? "'" + receiverFqn + "' and its supertypes"
                    : "'" + receiverFqn + "'";
            throw new JavaViolationException("overloaded method '" + name + "' ("
                    + signatures.size() + " signatures visible on " + where
                    + "); overloading is not supported");
        }
    }



    private StmtExpr convertJVerifyCall(MethodCallExpr call, String name) {
        return switch (name) {
            case "check" -> {
                if (call.getArguments().size() != 1) {
                    throw new JavaViolationException("check should have a single argument");
                }
                yield new StmtExpr.Assert(node(convertExpression(call.getArgument(0))), Optional.empty());
            }
            case "assume" -> {
                if (call.getArguments().size() != 1) {
                    throw new JavaViolationException("assume should have a single argument");
                }
                yield new StmtExpr.Assume(node(convertExpression(call.getArgument(0))));
            }
            case "implies" -> {
                if (call.getArguments().size() != 2) {
                    throw new JavaViolationException("implies should have two arguments");
                }
                var notA = primitiveOp(new Operation.Not(), node(convertExpression(call.getArgument(0))));
                yield primitiveOp(new Operation.Or(), node(notA), node(convertExpression(call.getArgument(1))));
            }
            case "jequals" -> {
                // Observational equality: Laurel's Eq is specified with
                // exactly jequals's semantics (== on primitives, reference
                // equality on impure composites), so it lowers to the same
                // node the translator emits for Java's == (JFE scheme).
                if (call.getArguments().size() != 2) {
                    throw new JavaViolationException("jequals should have two arguments");
                }
                yield primitiveOp(new Operation.Eq(),
                        node(convertExpression(call.getArgument(0))),
                        node(convertExpression(call.getArgument(1))));
            }
            case "forall", "exists" -> {
                if (call.getArguments().size() != 1
                        || !(call.getArgument(0) instanceof com.github.javaparser.ast.expr.LambdaExpr lambda)) {
                    throw new JavaViolationException(name + " requires a single lambda argument");
                }
                StmtExpr qBody = convertLambdaBody(lambda);
                for (int i = lambda.getParameters().size() - 1; i >= 0; i--) {
                    var p = lambda.getParameter(i);
                    var param = new org.strata.jverify.laurel.Parameter(
                            ident(p.getNameAsString()), node(translateType(p.getType())));
                    var mode = name.equals("forall")
                            ? (org.strata.jverify.laurel.QuantifierMode) new org.strata.jverify.laurel.QuantifierMode.Forall()
                            : new org.strata.jverify.laurel.QuantifierMode.Exists();
                    qBody = new StmtExpr.Quantifier(mode, param, Optional.empty(), node(qBody));
                }
                yield qBody;
            }
            default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
        };
    }
}
