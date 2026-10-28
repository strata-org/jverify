package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Condition;
import org.strata.jverify.laurel.ConditionMode;
import org.strata.jverify.laurel.ConstrainedType;
import org.strata.jverify.laurel.CoroutineContracts;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Identifier;
import org.strata.jverify.laurel.IncrDecrMode;
import org.strata.jverify.laurel.IncrDecrOp;
import org.strata.jverify.laurel.ModifiesGroup;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.LaurelProgram;
import org.strata.jverify.laurel.Raw;
import org.strata.jverify.laurel.SourceRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.laurel.Uri;
import org.strata.jverify.laurel.Variable;
import org.strata.java.bytecode.frontend.BytecodeFrontend.ClassSource;
import org.strata.jverify.verifier.IntervalTree;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;
import org.strata.java.bytecode.verifier.laurel.PositionResolver;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Compiles JVM classfiles to Laurel: binary ops as {@code $op} StaticCalls,
 * a constrained-integer prelude, cast helper procedures and a loop label
 * scheme, driven straight by the classfiles with CharacterRangeTable spans.
 *
 * <p>Position fidelity: the CRT gives statement-level source ranges. The
 * whole statement's range (minus trailing {@code ;}) stamps the translated
 * statement; contract clause ranges
 * are narrowed to the spec call's argument text. Sub-expression nodes
 * default to the enclosing statement's range.
 */
public class BytecodeToLaurelCompiler {

    /** Strata's reserved output-parameter name for the short return form. */
    public static final String LAUREL_RESULT_BINDING = LaurelFactory.LAUREL_RESULT_BINDING;

    private static final FileRange UNKNOWN_RANGE = SourceMap.UNKNOWN_RANGE;

    private final Reporter reporter;
    private final boolean verifyByDefault;
    /**
     * True when THIS run degraded its own compile (javac's {@code -Xjcov} CRT
     * computation crashed, so {@code BytecodeFrontend.compile} retried with
     * plain {@code -g}). The DEBUG-tier warning then has no advice to give --
     * it would tell the caller to do the thing that just crashed -- so it is
     * suppressed. A caller who really did hand over {@code -g}-only classfiles
     * still gets it.
     */
    private final boolean tierIsSelfInflicted;

    /** Whether {@link #analyze} runs the JVM bytecode-verification gate first. */
    private final boolean jvmVerifyInput;

    /** The library contracts bound by {@link #analyze} and consulted at call sites. */
    private final LibraryContracts contracts;

    /** Positional service: current file text/index plus span-to-range helpers. */
    private final SourceMap src = new SourceMap();
    /** Laurel node builders and the cast-helper procedure registry. */
    private final LaurelFactory lf = new LaurelFactory(src);

    // Per-run state
    private final List<Procedure> procedures = new ArrayList<>();
    /** Composite-sort registry: referenced sorts, accessed fields, extends links, instance procs. */
    private final CompositeModel composites;
    /** The program's type declarations: type trees, @Contract/@Transparent markers, method sets. */
    private final Declarations decls = new Declarations();
    /** The type universe: name resolution, hierarchy walks, classfile policy gates. */
    private final ClassResolver resolver;
    /** javac classpath entries; consulted by the JVM bytecode-verification gate's hierarchy resolver. */
    private final List<java.nio.file.Path> classPath;

    private final Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses = new HashMap<>();


    /** Synthesized receiver parameter on instance procedures (see {@link LaurelFactory#LAUREL_SELF_BINDING}). */
    static final String LAUREL_SELF_BINDING = LaurelFactory.LAUREL_SELF_BINDING;

    /** Per-method accounting: refusal fixpoint, callee edges, refusal diagnostics. */
    private final MethodGraph graph;
    /** Synthesized name binding the thrown value in a `throws (e: T)` clause. */
    private static final String THROWN_BINDING = LaurelFactory.THROWN_BINDING;

    public BytecodeToLaurelCompiler(Reporter reporter) {
        this(reporter, true, List.of());
    }

    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault) {
        this(reporter, verifyByDefault, List.of());
    }

    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault,
                                    List<java.nio.file.Path> classPath) {
        this(reporter, verifyByDefault, classPath, false);
    }

    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault,
                                    List<java.nio.file.Path> classPath,
                                    boolean tierIsSelfInflicted) {
        this(reporter, verifyByDefault, classPath, tierIsSelfInflicted, true);
    }

    /**
     * @param jvmVerifyInput run the JVM bytecode-verification gate (see
     *        {@link #reportUnverifiableClassfiles}) before translation. The
     *        shorter constructors enable it, since this class cannot know
     *        where its classfiles came from; a caller that produced them with
     *        javac itself may skip it, as javac output always verifies.
     */
    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault,
                                    List<java.nio.file.Path> classPath,
                                    boolean tierIsSelfInflicted,
                                    boolean jvmVerifyInput) {
        this(reporter, verifyByDefault, classPath, tierIsSelfInflicted, jvmVerifyInput,
                LibraryContracts.none());
    }

    /**
     * @param contracts the library contracts this run binds and consults
     *        ({@link LibraryContracts#none()} applies none)
     */
    public BytecodeToLaurelCompiler(Reporter reporter, boolean verifyByDefault,
                                    List<java.nio.file.Path> classPath,
                                    boolean tierIsSelfInflicted,
                                    boolean jvmVerifyInput,
                                    LibraryContracts contracts) {
        this.tierIsSelfInflicted = tierIsSelfInflicted;
        this.jvmVerifyInput = jvmVerifyInput;
        this.contracts = contracts;
        this.reporter = reporter;
        this.verifyByDefault = verifyByDefault;
        this.classPath = List.copyOf(classPath);
        this.resolver = new ClassResolver(classPath, verifyByDefault);
        this.composites = new CompositeModel(resolver, contracts);
        this.graph = new MethodGraph(reporter);
        shouldVerifies.push(verifyByDefault ? ShouldVerifyMode.DefaultYes : ShouldVerifyMode.DefaultNo);
    }

    /** javac's account of the classes the input's classfiles mention, which the hierarchy walks take first. */
    public BytecodeToLaurelCompiler withJavacTypes(Map<String, org.strata.java.bytecode.frontend.TypeFacts> types) {
        resolver.useJavacTypes(types);
        return this;
    }

    public record AnalysisResult(LaurelProgram program,
                                 PositionResolver positions,
                                 Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses) {
    }

    /**
     * @param sourceUris the input source-file URIs (paths only, NO content):
     *        each class is matched to its URI by the SourceFile attribute's
     *        file name so diagnostics carry the right file. All positions come
     *        from the bytecode (CRT/LNT), never from the source text.
     * @param classes the pooled top-level classes
     */
    public AnalysisResult analyze(Collection<URI> sourceUris, List<ClassSource> classes) {
        if (jvmVerifyInput && reportUnverifiableClassfiles(sourceUris, classes)) {
            // Self-enforcing, like the tier refusal below: bytecode that
            // fails JVM verification never executes on a JVM, and the
            // operand-stack simulation downstream assumes verifiable input.
            throw new RefusedInputException(
                    "input failed JVM bytecode verification; see the reported errors");
        }
        if (reportInputTier(sourceUris, classes)) {
            // Self-enforcing: a caller that never checks the reporter must
            // not translate (and then "verify") an input we refused.
            throw new RefusedInputException(
                    "input carries no debug information; see the reported error");
        }
        var units = new ArrayList<ClassSource>();
        for (var cls : classes) {
            if (cls.internalName().equals("package-info") || cls.internalName().endsWith("/package-info")) {
                packageInfos.put(packageOf(cls.internalName()), cls.classModel());
            } else {
                units.add(cls);
            }
        }
        classes = units;
        for (var cls : classes) {
            String fqn = JavaNames.dottedFqn(cls.internalName());
            resolver.knownClasses.put(fqn, fqn);
            resolver.knownClasses.put(JavaNames.simpleName(fqn), fqn);
            resolver.programClassModels.put(fqn, cls.classModel());
            for (var nested : cls.nestedModels()) {
                resolver.programClassModels.put(
                        JavaNames.dottedFqn(nested.thisClass().asInternalName()), nested);
            }
            // Superclass edges straight from the classfiles: authoritative for
            // the exception-hierarchy walks (isSubclassOf / LCA / extends links).
            cls.classModel().superclass().ifPresent(sup ->
                    resolver.programSuper.put(fqn, JavaNames.dottedFqn(sup.asInternalName())));
            for (var nested : cls.nestedModels()) {
                String nestedFqn = JavaNames.dottedFqn(nested.thisClass().asInternalName());
                nested.superclass().ifPresent(sup ->
                        resolver.programSuper.put(nestedFqn, JavaNames.dottedFqn(sup.asInternalName())));
            }
            // Nested classes resolve by simple name and by Outer.Inner form,
            // so `Point p` and `new Point()` qualify to
            // the same composite sort as the field declarations.
            for (var nested : cls.nestedModels()) {
                String nestedFqn = JavaNames.dottedFqn(nested.thisClass().asInternalName());
                resolver.knownClasses.put(nestedFqn, nestedFqn);
                resolver.knownClasses.put(JavaNames.simpleName(nestedFqn), nestedFqn);
                String outerSimple = JavaNames.simpleName(fqn);
                if (nestedFqn.startsWith(fqn + ".")) {
                    resolver.knownClasses.put(outerSimple + nestedFqn.substring(fqn.length()), nestedFqn);
                }
            }
        }
        // The program class set is final: decide array-model availability once.
        composites.arrays.computeEnabled();

        // Pre-pass: collect the type trees, @Contract markers, @Transparent markers,
        // and declared fields of every class in the compilation, so field
        // accesses and the heap-write fallback resolve regardless of order.
        var typeTrees = new HashMap<String, Declarations.TypeNode>();
        for (var cls : classes) {
            String fqn = JavaNames.dottedFqn(cls.internalName());
            URI uri = sourceUriFor(cls, sourceUris);
            Declarations.TypeNode root = Declarations.typeTreeOf(cls);
            typeTrees.put(cls.internalName(), root);
            decls.collect(root, uri);
            // The translator's field registry, straight from the classfiles
            // (descriptor + Signature + type annotations).
            collectDeclaredFields(fqn, cls.classModel());
            for (var nested : cls.nestedModels()) {
                collectDeclaredFields(
                        JavaNames.dottedFqn(nested.thisClass().asInternalName()),
                        nested);
            }
            collectNestedTreeFields(root);
        }

        new ContractValidator(reporter, src, decls, resolver).validate();
        // A relocated source contract's fields become the contractee's (appended
        // to the contractee's members).
        for (var e : decls.sourceContracts.entrySet()) {
            collectDeclaredFields(e.getKey(), e.getValue().cm());
        }
        for (var e : decls.libraryContracts.entrySet()) {
            String sort = resolver.libraryContractSort(e.getKey());
            resolver.contracteeOf.put(e.getValue().fqn(), sort);
            collectDeclaredFields(sort, e.getValue().decl().cm());
        }

        // Library contracts: bind this run's resolver, the program's own
        // @Contract targets (whose library contracts are not applied), and the
        // lazy-load warning anchor; contractSourcePath sources load eagerly.
        contracts.bind(resolver, programContractTargets(), reporter,
                sourceUris.isEmpty() ? null : sourceUris.iterator().next());
        if (contracts.hasUserSources()) {
            contracts.ensureLoaded();
        }
        checkContractLoad();

        for (var cls : classes) {
            src.uri = sourceUriFor(cls, sourceUris);
            compileUnit(cls, typeTrees.get(cls.internalName()));
        }
        emitLibraryContracts();
        // A call site may have triggered the lazy load.
        checkContractLoad();

        translateLibraryMembers();

        // Refusal propagation: a method
        // that calls a refused one is itself demoted -- soundness, since its
        // callee's contract was never established. Iterated to a fixpoint so
        // A -> B -> C chains demote fully. Library members no surviving
        // program method reaches are then refused too, and every refused
        // method's emitted procedure is dropped (an InstanceCall/StaticCall to
        // an unemitted procedure would die in Strata resolution).
        inlinePreconditionOf();
        demoteCallersOfRefusedMethods();
        graph.pruneUnreachableLibraryMembers();
        stripCallsToUntranslatedConstructors();
        dropRefusedProcedures();

        // Composite declarations: every referenced class-typed sort together
        // with its full Java supertype closure (superclass chain + interfaces,
        // transitively including Object), each with only its accessed fields.
        // Every sort `extends` its DIRECT
        // supertypes and Strata walks those edges transitively, so a subtype
        // value flows to any supertype-typed position exactly as Java subtyping
        // allows. ANCESTORS FIRST: Laurel builds a composite's scope by copying
        // each parent's, so a parent must be declared before its children.
        List<TypeDefinition> types = new ArrayList<>(getPredefinedTypes());
        var emittedComposites = new java.util.HashSet<String>();
        if (composites.referencedCompositeTypes.contains(ArrayModel.SORT)) {
            // Before any composite whose fields or procedures name an array:
            // resolution reads a field (contentsVersion in a frame) only on
            // a composite declared earlier.
            emitCompositeClosure(ArrayModel.SORT, emittedComposites, types);
        }
        for (var typeName : new ArrayList<>(composites.referencedCompositeTypes)) {
            if (composites.lambdaSupertypes.containsKey(typeName)) {
                emitCompositeClosure(typeName, emittedComposites, types);
            }
        }
        for (var typeName : new ArrayList<>(composites.referencedCompositeTypes)) {
            emitCompositeClosure(typeName, emittedComposites, types);
        }
        List<Procedure> allProcedures = new ArrayList<>(emittedHelpers());
        allProcedures.addAll(translationContext().missingContractProcs.values());
        allProcedures.addAll(procedures);
        var program = new LaurelProgram(allProcedures, List.of(), List.of(), types, List.of());

        var knownUris = new java.util.HashSet<URI>(sourceUris);
        knownUris.addAll(contracts.loadedUris());
        return new AnalysisResult(program,
                positionResolver(java.util.Set.copyOf(knownUris)), methodStatuses);
    }

    /**
     * The registered helper procedures, in first-use order, without an array
     * wrapper that no emitted procedure calls: a wrapper calls a JArray
     * member, which is emitted only while a surviving procedure reaches it.
     */
    private List<Procedure> emittedHelpers() {
        if (!lf.castHelpers.containsKey(ArrayModel.GET_WRAPPER)
                && !lf.castHelpers.containsKey(ArrayModel.LENGTH_WRAPPER)) {
            return new ArrayList<>(lf.castHelpers.values());
        }
        var called = new java.util.HashSet<String>();
        for (var p : procedures) {
            collectProcedureCallees(p, called);
        }
        for (var procs : composites.instanceProcsBySort.values()) {
            for (var p : procs) {
                collectProcedureCallees(p, called);
            }
        }
        var out = new ArrayList<Procedure>();
        for (var e : lf.castHelpers.entrySet()) {
            if (ArrayModel.wrappedMember(e.getKey()) == null || called.contains(e.getKey())) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    /** The callee names of every call in {@code p}'s conditions, body, frames and throwsOn blocks. */
    private static void collectProcedureCallees(Procedure p, java.util.Set<String> out) {
        for (var c : p.preconditions()) {
            collectCalleeNames(c.condition(), out);
        }
        switch (p.body()) {
            case Body.Transparent t -> collectCalleeNames(t.body(), out);
            case Body.Opaque o -> {
                for (var c : o.postconditions()) {
                    collectCalleeNames(c.condition(), out);
                }
                o.implementation().ifPresent(impl -> collectCalleeNames(impl, out));
                for (var g : o.modifies()) {
                    for (var t : g.targets()) {
                        collectCalleeNames(t, out);
                    }
                    g.guard().ifPresent(guard -> collectCalleeNames(guard, out));
                }
            }
            case Body.Abstract a -> {
                for (var c : a.postconditions()) {
                    collectCalleeNames(c.condition(), out);
                }
            }
            default -> {
            }
        }
        for (var block : p.throwsOn()) {
            collectCalleeNames(block.guard(), out);
            for (var c : block.postconditions()) {
                collectCalleeNames(c.condition(), out);
            }
            for (var t : block.modifies()) {
                collectCalleeNames(t, out);
            }
        }
    }

    /**
     * The library types the program's own {@code @Contract} classes target
     * ({@link ContractValidator}'s rule: the explicit target, else the first
     * declared supertype).
     */
    private java.util.Set<String> programContractTargets() {
        var targets = new java.util.HashSet<String>();
        for (var entry : decls.contractDecls) {
            String target = Declarations.explicitContractTarget(entry.decl().cm());
            if (target == null) {
                var supers = Declarations.declaredSuperFqns(entry.decl().cm());
                if (!supers.isEmpty()) {
                    target = supers.get(0);
                }
            }
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    /** End the run without a verdict when the contract sources failed to load fatally. */
    private void checkContractLoad() {
        if (contracts.fatalLoadError()) {
            throw new RefusedInputException("contract sources could not be loaded; see the reported errors");
        }
    }

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(BytecodeToLaurelCompiler.class.getName());

    /** The library member behind each library {@link MethodGraph.MethodInfo}, by info key. */
    private final Map<String, ContractMember> libraryMembers = new LinkedHashMap<>();
    /** Contract classes whose fields are already registered on their contractee sort. */
    private final java.util.Set<String> registeredContractClasses = new java.util.HashSet<>();

    /**
     * Translate the library contract members program calls demanded, after
     * the program's units, in demand order (FIFO; a member's own calls may
     * demand more). Contract mode is on throughout, with contract class names
     * aliased to their contractee sorts. Then refuse every emitted member a
     * program class or another library member overrides.
     */
    private void translateLibraryMembers() {
        if (!contracts.hasDemand()) {
            return;
        }
        var ctx = translationContext();
        resolver.contractAliases = contracts.aliases();
        ctx.contractMode = true;
        try {
            ContractMember m;
            while ((m = contracts.nextDemand()) != null) {
                registerContractFieldsOnce(m);
                translateLibraryMember(m);
            }
            refuseOverriddenLibraryMembers();
        } finally {
            resolver.contractAliases = Map.of();
            ctx.contractMode = false;
            src.uri = null;
        }
    }

    /**
     * Translate one library member into a procedure on its sort (instance) or
     * a {@code <owner>_<name>} procedure (static). A member without code or a
     * spec-interface method becomes a declaration only. A member that does
     * not translate is refused with its reason, which its callers' demotion
     * diagnostics carry; it is never diagnosed itself, except for a
     * postcondition on a {@code @Transparent} member.
     */
    private void translateLibraryMember(ContractMember m) {
        src.uri = m.uri();
        src.defaultRange = UNKNOWN_RANGE;
        var info = new MethodGraph.MethodInfo(m.sortOrFqn(), m.laurelName(), m.laurelName(), m.isStatic(), m.uri(),
                Reporter.NO_RANGE, 0, /*quiet*/ false, /*library*/ true);
        graph.methodInfos.put(info.key(), info);
        libraryMembers.put(info.key(), m);
        try {
            if (m.kind() == ContractMember.Kind.ARRAY_MODEL
                    && ArrayModel.shapeProblem(contracts.classBytes(m.classInternal())) != null) {
                throw new JavaViolationException(ArrayModel.UNAVAILABLE);
            }
            if (m.kind() == ContractMember.Kind.SPEC_INTERFACE || !m.hasCode()) {
                emitDeclarationOnly(m, info);
            } else {
                var cm = memberClassModel(m);
                translateMethod(m.sortOrFqn(), cm, memberMethodModel(cm, m), info,
                        /*verify*/ true, false, m);
            }
        } catch (RefusedInputException e) {
            throw e;
        } catch (ReportedViolation e) {
            info.refused = true;
        } catch (JavaViolationException e) {
            info.refused = true;
            info.refusalReason = e.getMessage();
        } catch (RuntimeException e) {
            info.refused = true;
            info.refusalReason = "internal error translating contract: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage();
            LOG.log(java.util.logging.Level.FINE, "internal error translating contract member "
                    + m.calleeKey(), e);
        }
    }

    /** A fresh model of the class declaring {@code m}: contract and helper classes from the cached bytes. */
    private ClassModel memberClassModel(ContractMember m) {
        ClassModel cm = m.kind() == ContractMember.Kind.SPEC_INTERFACE
                ? resolver.loadClassModel(m.classInternal().replace('/', '.'))
                : contracts.classBytes(m.classInternal());
        if (cm == null) {
            throw new IllegalStateException("no classfile for " + m.classInternal());
        }
        return cm;
    }

    /** The method {@code m} names in {@code cm}, by name and its own descriptor. */
    private static java.lang.classfile.MethodModel memberMethodModel(ClassModel cm, ContractMember m) {
        for (var mm : cm.methods()) {
            if (mm.methodName().stringValue().equals(m.methodName())
                    && mm.methodTypeSymbol().equals(m.methodDesc())) {
                return mm;
            }
        }
        throw new IllegalStateException("no method " + m.methodName() + m.methodDesc().descriptorString()
                + " in " + m.classInternal());
    }

    /**
     * A library member's body. With no implementation, an effective
     * {@code @Verify(false)}, postconditions, or an explicit {@code modifies},
     * the implementation is dropped: {@code Opaque([],none,[])} without
     * conditions, {@code Abstract(posts)} with postconditions only, else
     * {@code Opaque(posts,none,modifies)}. Otherwise (a transparent member
     * with a real body) the body is the definition: {@code Transparent},
     * whatever its shape, so Strata reports a body it does not accept, such
     * as one with a loop. So an opted-out member is
     * never {@code Transparent} and Strata never checks its body. The caller
     * refuses a {@code Transparent} member whose result type is
     * range-constrained ({@code int}, {@code char}, ...).
     */
    private static Body libraryMemberBody(ContractMember member, boolean noImplementation,
                                          List<Condition> postconditions,
                                          List<AstNode<StmtExpr>> explicitModifies,
                                          List<AstNode<StmtExpr>> implementation, StmtExpr methodBody) {
        if (noImplementation || !member.verify() || !postconditions.isEmpty()
                || !explicitModifies.isEmpty()) {
            if (!explicitModifies.isEmpty()) {
                return new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(explicitModifies));
            }
            if (!postconditions.isEmpty()) {
                return new Body.Abstract(postconditions);
            }
            return new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()));
        }
        return new Body.Transparent(noRange(methodBody));
    }

    /**
     * A library member without code (abstract, or a spec-interface method)
     * as an uninterpreted procedure: its signature with an
     * {@code Opaque([],none,[])} body.
     */
    private void emitDeclarationOnly(ContractMember m, MethodGraph.MethodInfo info) {
        var cm = memberClassModel(m);
        var mm = memberMethodModel(cm, m);
        java.lang.classfile.MethodSignature msig = methodSignatureOf(mm);
        var methodTypeParams = new java.util.LinkedHashSet<String>();
        if (msig != null) {
            msig.typeParameters().forEach(tp -> methodTypeParams.add(tp.identifier()));
        }
        var typeParams = new java.util.HashSet<String>(methodTypeParams);
        typeParams.addAll(Declarations.typeParamNamesOf(cm));
        var ctx = translationContext();
        ctx.currentClassFqn = m.sortOrFqn();
        ctx.typeParams = typeParams;
        ctx.methodTypeParams = methodTypeParams;
        ctx.impureTypeParams = Set.of();
        ctx.beginMethod(info);
        var signature = buildSignature(m.sortOrFqn(), cm, mm, null, m.isStatic(), typeParams, methodTypeParams);
        refuseUnfixedTypeParams(methodTypeParams, signature.inputs());
        String name = m.isStatic() ? m.sortOrFqn() + "_" + m.laurelName() : m.laurelName();
        var proc = new Procedure(
                ident(name),
                methodTypeParams.stream().map(LaurelFactory::ident).toList(),
                signature.inputs(), signature.outputs(), List.of(), new CoroutineContracts.Regular(),
                Optional.empty(), new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of())),
                Optional.empty(), false, List.of(),
                Optional.empty(), Optional.empty(), List.of(),
                /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
        if (m.isStatic()) {
            procedures.add(proc);
        } else {
            composites.instanceProcsBySort.computeIfAbsent(m.sortOrFqn(), k -> new ArrayList<>()).add(proc);
        }
        info.proc = proc;
        info.sort = m.isStatic() ? null : m.sortOrFqn();
    }

    /**
     * Refuse a library member with a method type parameter that no input
     * type mentions ({@code Optional.empty()}): Strata cannot infer its type
     * argument at a call, because the emitted result type is the erased sort.
     */
    private static void refuseUnfixedTypeParams(java.util.Set<String> methodTypeParams,
                                                List<org.strata.jverify.laurel.Parameter> inputs) {
        for (String tp : methodTypeParams) {
            boolean fixed = false;
            for (var p : inputs) {
                fixed |= mentionsTypeVar(p.type().val(), tp);
            }
            if (!fixed) {
                throw new JavaViolationException("type parameter '" + tp
                        + "' that no argument fixes is not yet supported");
            }
        }
    }

    /** True iff an output has a range-constrained type ({@code int32}, {@code char}, {@code nat}, ...). */
    private static boolean hasConstrainedOutput(List<org.strata.jverify.laurel.Parameter> outputs) {
        for (var p : outputs) {
            if (p.type().val() instanceof HighType.UserDefined u
                    && LaurelFactory.BOUNDS.containsKey(u.name().text())) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code t} mentions the type variable {@code name}. */
    private static boolean mentionsTypeVar(HighType t, String name) {
        return switch (t) {
            case HighType.TVar v -> v.name().text().equals(name);
            case HighType.TSet s -> mentionsTypeVar(s.elementType().val(), name);
            case HighType.TMap m -> mentionsTypeVar(m.keyType().val(), name)
                    || mentionsTypeVar(m.valueType().val(), name);
            case HighType.Applied a -> mentionsTypeVar(a.base().val(), name)
                    || a.typeArguments().stream().anyMatch(x -> mentionsTypeVar(x.val(), name));
            case HighType.Intersection i -> i.types().stream().anyMatch(x -> mentionsTypeVar(x.val(), name));
            case HighType.MultiValuedExpr mv -> mv.types().stream().anyMatch(x -> mentionsTypeVar(x.val(), name));
            default -> false;
        };
    }

    /**
     * The first time a member of a contract class is translated, register
     * that class's instance fields on its contractee sort, immutable when the
     * contract says {@code pure = true}. The first JArray member registers
     * the JArray class's instance field on the {@code JArray} composite,
     * with the composite's type parameter. Other helper classes and
     * spec-interface classes contribute no fields.
     */
    private void registerContractFieldsOnce(ContractMember m) {
        if (m.kind() == ContractMember.Kind.ARRAY_MODEL && registeredContractClasses.add(m.classInternal())) {
            var cm = contracts.classBytes(m.classInternal());
            if (cm != null) {
                collectDeclaredFields(ArrayModel.SORT, cm, /*skipStatic*/ true, /*allFinal*/ false);
                composites.arrays.registerComposite();
            }
            return;
        }
        if (m.kind() != ContractMember.Kind.CONTRACT || !registeredContractClasses.add(m.classInternal())) {
            return;
        }
        var cc = contracts.contractClassOf(m.classInternal());
        var cm = contracts.classBytes(m.classInternal());
        if (cc == null || cm == null) {
            return;
        }
        collectDeclaredFields(cc.sort(), cm, /*skipStatic*/ true, /*allFinal*/ cc.pure());
    }

    /**
     * Refuse every emitted instance library member that an override would
     * refine, since override refinement against library contracts is not
     * supported: a program class below the contractee declaring a method of
     * the same name, or, for a member on {@code Object}, another emitted
     * library member of the same name (every library sort extends
     * {@code Object} in the emitted Laurel).
     */
    private void refuseOverriddenLibraryMembers() {
        var live = new ArrayList<MethodGraph.MethodInfo>();
        for (var info : graph.methodInfos.values()) {
            if (info.library && !info.refused && !info.isStatic) {
                live.add(info);
            }
        }
        for (var info : live) {
            var member = libraryMembers.get(info.key());
            String javaName = javaNameOf(info.name);
            String overrider = null;
            for (var e : resolver.programClassModels.entrySet()) {
                if (!resolver.isSubtypeOf(e.getKey(), member.contracteeFqn())) {
                    continue;
                }
                for (var pm : Declarations.declaredMethodsOf(e.getValue())) {
                    if (pm.methodName().stringValue().equals(javaName)
                            && !pm.flags().has(java.lang.reflect.AccessFlag.STATIC)
                            && !pm.flags().has(java.lang.reflect.AccessFlag.SYNTHETIC)) {
                        overrider = e.getKey();
                        break;
                    }
                }
                if (overrider != null) {
                    break;
                }
            }
            if (overrider == null && info.classFqn.equals(OBJECT_SORT)) {
                for (var other : live) {
                    if (other != info && !other.classFqn.equals(OBJECT_SORT)
                            && javaNameOf(other.name).equals(javaName)) {
                        overrider = other.classFqn;
                        break;
                    }
                }
            }
            if (overrider != null) {
                info.refused = true;
                info.refusalReason = "overridden by '" + overrider
                        + "'; override refinement against library contracts is not yet supported";
            }
        }
    }

    /** A library member's Java method name: its Laurel name up to the first {@code '?'}. */
    private static String javaNameOf(String laurelName) {
        int mangle = laurelName.indexOf('?');
        return mangle >= 0 ? laurelName.substring(0, mangle) : laurelName;
    }

    /**
     * Caller demotion: each round marks every method that calls an
     * already-refused one, reporting {@code call to
     * 'X', which could not be translated} (severity by the CALLER's own
     * staticness, anchored at the caller's declaration name, deduped per
     * (caller, reason) -- the demotion itself is never deduped). Iterated to
     * a fixpoint: A -> B -> C with C refused demotes B in round one and A in
     * round two. Terminates because the refused set only grows and is
     * bounded by the method count.
     */
    private void demoteCallersOfRefusedMethods() {
        graph.demoteCallersOfRefusedMethods();
    }

    /**
     * Drop the emitted procedures of demoted methods (the post-fixpoint
     * drop pass): their bodies reference unemitted callees, and Strata would
     * fail resolution on them.
     */
    private void dropRefusedProcedures() {
        graph.dropRefusedProcedures(procedures, composites);
    }

    /**
     * Populate the direct translator's field registry from a classfile:
     * name -> TypeRef (generic Signature preferred, else the descriptor,
     * with @Nat/@Unbounded from RuntimeInvisibleTypeAnnotations) + finality.
     * Synthetic fields ($assertionsDisabled, this$0, $VALUES) and enum
     * constants are skipped — they are declaration syntax, not fields.
     */
    private void collectNestedTreeFields(Declarations.TypeNode node) {
        for (var nested : node.nested()) {
            if (!nested.fqn().equals(JavaNames.dottedFqn(nested.cm().thisClass().asInternalName()))) {
                collectDeclaredFields(nested.fqn(), nested.cm());
            }
            collectNestedTreeFields(nested);
        }
    }

    private void collectDeclaredFields(String fqn, java.lang.classfile.ClassModel cm) {
        collectDeclaredFields(fqn, cm, false, false);
    }

    /**
     * As above, into {@code fqn}'s registry: {@code skipStatic} also skips
     * static fields, and {@code allFinal} registers every field as final.
     */
    private void collectDeclaredFields(String fqn, java.lang.classfile.ClassModel cm,
                                       boolean skipStatic, boolean allFinal) {
        var fields = composites.declaredFields.computeIfAbsent(fqn, k -> new LinkedHashMap<>());
        for (var fm : cm.fields()) {
            int flags = fm.flags().flagsMask();
            if ((flags & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                    | java.lang.classfile.ClassFile.ACC_ENUM)) != 0
                    && !fm.fieldName().stringValue().startsWith("this$")
                    && !fm.fieldName().stringValue().startsWith("val$")) {
                continue;
            }
            if (skipStatic && (flags & java.lang.classfile.ClassFile.ACC_STATIC) != 0) {
                continue;
            }
            boolean isNat = false;
            boolean isUnbounded = false;
            boolean nullable = hasNullableDeclarationAnnotation(fm);
            for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(fm)) {
                nullable |= ta.targetInfo().targetType() == java.lang.classfile.TypeAnnotation.TargetType.FIELD
                        && ArrayModel.isNullableAnnotation(ta.annotation().className().stringValue());
                if (!ta.targetPath().isEmpty()
                        || ta.targetInfo().targetType()
                                != java.lang.classfile.TypeAnnotation.TargetType.FIELD) {
                    continue;
                }
                String anno = java.lang.constant.ClassDesc
                        .ofDescriptor(ta.annotation().className().stringValue()).displayName();
                isNat |= anno.equals("Nat");
                isUnbounded |= anno.equals("Unbounded");
            }
            TypeRef t = null;
            var sig = fm.findAttribute(java.lang.classfile.Attributes.signature()).orElse(null);
            if (sig != null) {
                try {
                    t = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries.renderSig(
                            java.lang.classfile.Signature.parseFrom(sig.signature().stringValue())),
                            isNat, isUnbounded);
                } catch (RuntimeException ignore) {
                    // fall back to the descriptor
                }
            }
            if (t == null) {
                t = TypeRef.ofDescriptor(fm.fieldTypeSymbol().descriptorString(), isNat, isUnbounded);
            }
            fields.put(fm.fieldName().stringValue(), new CompositeModel.FieldInfo(
                    t, allFinal || (flags & java.lang.classfile.ClassFile.ACC_FINAL) != 0, nullable));
        }
    }

    /** True iff {@code el} carries a {@code Nullable} declaration annotation (visible or invisible). */
    private static boolean hasNullableDeclarationAnnotation(java.lang.classfile.AttributedElement el) {
        var annos = new ArrayList<java.lang.classfile.Annotation>();
        el.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                .ifPresent(a -> annos.addAll(a.annotations()));
        el.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .ifPresent(a -> annos.addAll(a.annotations()));
        return annos.stream().anyMatch(a -> ArrayModel.isNullableAnnotation(a.className().stringValue()));
    }

    // --- External @Contract validation ---

    /** Load a classfile from the javac classpath (dirs and jars), or null. */
    private java.lang.classfile.ClassModel loadClassModel(String fqn) {
        return resolver.loadClassModel(fqn);
    }

    /** Match a pooled class to its source file by the SourceFile attribute name. */
    private static URI sourceUriFor(ClassSource cls, Iterable<URI> sourceUris) {
        return SourceMap.sourceUriFor(cls, sourceUris);
    }

    /**
     * The diagnostic position resolver for a run over {@code knownUris}:
     * unpack a synthetic (line, column) offset via {@link PositionCodec}
     * (Strata echoes it back opaquely), but throw for a URI that was never a
     * frontend input -- so the diagnostic parser degrades it to a counted
     * malformed-diagnostic instead of fabricating a position for a phantom
     * file.
     */
    static PositionResolver positionResolver(java.util.Set<URI> knownUris) {
        return (uri, offset) -> {
            if (!knownUris.contains(uri)) {
                throw new IllegalArgumentException(
                        "diagnostic for a file that was not a frontend input: " + uri);
            }
            return PositionCodec.decode(offset);
        };
    }

    // --- Node helpers (extracted to {@link LaurelFactory}; thin delegates keep call sites stable) ---

    private static List<ModifiesGroup> unguardedModifies(List<AstNode<StmtExpr>> targets) {
        return LaurelFactory.unguardedModifies(targets);
    }

    private static Identifier ident(String name) {
        return LaurelFactory.ident(name);
    }

    private AstNode<StmtExpr> node(StmtExpr expr) {
        return lf.node(expr);
    }

    private AstNode<StmtExpr> node(StmtExpr expr, FileRange range) {
        return lf.node(expr, range);
    }

    private AstNode<Variable> nodeVar(Variable v) {
        return lf.nodeVar(v);
    }

    private static AstNode<HighType> node(HighType type) {
        return LaurelFactory.node(type);
    }

    private static StmtExpr var_(String name) {
        return LaurelFactory.var_(name);
    }

    @SafeVarargs
    private static StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return LaurelFactory.primitiveOp(op, args);
    }

    // --- Predefined constrained integer types (extracted to {@link LaurelFactory}) ---

    private List<TypeDefinition> getPredefinedTypes() {
        return LaurelFactory.getPredefinedTypes();
    }

    private static AstNode<StmtExpr> noRange(StmtExpr e) {
        return LaurelFactory.noRange(e);
    }

    private StmtExpr longLiteral(long val) {
        return LaurelFactory.longLiteral(val);
    }

    // --- Ranges from CRT spans (extracted to {@link SourceMap}) ---

    private Range positionRange(FileRange range) {
        return src.positionRange(range);
    }

    // --- Contract extraction ---

    // --- Types ---

    // --- Exception hierarchy (superclass chain, subclass test, LCA, extends edges) ---

    /**
     * Resolve a class reference to an fqn: this compilation's
     * classes first, then the unit's single-type imports, then an already-
     * qualified name as-is, then {@code java.lang}.
     */
    private String resolveClassFqn(String name) {
        return resolver.resolveClassFqn(name);
    }

    /** The Laurel sort name of {@code java.lang.Object}. */
    private static final String OBJECT_SORT = CompositeModel.OBJECT_SORT;

    /**
     * Emit composite {@code sortName} and its full supertype closure, ANCESTORS
     * FIRST (parents before children, as Laurel requires for scope copying).
     * Each sort {@code extends} its direct supertypes (superclass +
     * interfaces); a sort with no supertype
     * other than itself gets an explicit {@code Object} parent (an interface
     * has no superclass edge -- JLS 4.10.2 -- and a synthetic lambda class
     * carries no supertype). Strata walks these edges transitively, so a value
     * of a subtype flows to any supertype-typed position.
     */
    // Package-private (not private) so ClosureHelpersTest can exercise the
    // supertype-closure helpers below in isolation.
    void emitCompositeClosure(String sortName, java.util.Set<String> emitted, List<TypeDefinition> out) {
        composites.emitCompositeClosure(sortName, emitted, out);
    }

    /**
     * The sort names of {@code sortName}'s DIRECT Java supertypes: its
     * superclass (if any) followed by its directly-implemented interfaces.
     * Program types resolve from their classfiles; JCL/library types via
     * reflection on the loaded {@code Class} (never classpath resources, which
     * are unavailable on the build fleet). {@code java.lang.Object} maps to the
     * {@code Object} sort.
     */
    List<String> directSupertypeSorts(String sortName) {
        return composites.directSupertypeSorts(sortName);
    }

    /** Reflectively load a composite sort name (see {@link ClassResolver#loadClassReflectively}). */
    Class<?> loadClassReflectively(String dottedName) {
        return resolver.loadClassReflectively(dottedName);
    }

    /** Direct interface fqns of {@code fqn} (classfile if a program type, else reflection). */
    List<String> directInterfacesOf(String fqn) {
        return resolver.directInterfacesOf(fqn);
    }

    /** The composite sort name for an fqn: {@code Object} for java.lang.Object, else the dotted fqn. */
    String sortNameForFqn(String fqn) {
        return CompositeModel.sortNameForFqn(fqn);
    }

    /** True iff {@code fqn} is an interface (classfile flags if a program type, else reflection). */
    boolean isInterface(String fqn) {
        return resolver.isInterface(fqn);
    }

    private String superclassOf(String fqn) {
        return resolver.superclassOf(fqn);
    }

    /** True iff {@code fqn} is {@code ancestor} or below it on the superclass chain. */
    private boolean isSubclassOf(String fqn, String ancestor) {
        return resolver.isSubclassOf(fqn, ancestor);
    }

    /** True iff {@code fqn} has {@code java.lang.Throwable} on its superclass chain. */
    private boolean isThrowableSubtype(String fqn) {
        return resolver.isThrowableSubtype(fqn);
    }

    private String leastCommonAncestor(String a, String b) {
        return resolver.leastCommonAncestor(a, b);
    }

    /**
     * Record that composite {@code child} extends {@code parent}, declaring
     * both sorts. Membership in composites.referencedCompositeTypes and the edge in
     * compositeExtends always move together, so a linked child cannot
     * silently miss its declaration; a conflicting parent edge is refused
     * loudly.
     */
    private void linkExtends(String childSort, String parentSort) {
        composites.linkExtends(childSort, parentSort);
    }

    /**
     * Declare {@code fqn} and its superclass chain up to and including
     * {@code java.lang.Throwable} (or the top of the chain if Throwable is
     * not an ancestor) as composites with their extends links. Registering
     * all the way up -- not just to a declared join -- is what keeps every
     * {@code is} test between exception types meaningful: a caller may catch
     * any supertype of a declared type, and that guard needs the chain
     * linked.
     */
    private void registerHierarchyUpToThrowable(String fqn) {
        composites.registerHierarchyUpToThrowable(fqn);
    }

    // --- Per-class compilation ---

    // --- @Verify stack (per-scope verification modes) ---

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

    /**
     * Report a method's translation refusal with a severity asymmetry: a
     * static method's failure is an error ({@code translatorError={0}}), an
     * instance method's a warning ({@code instanceMethodNotVerified}) --
     * instance support is deliberately partial, so a refusal demotes to an
     * honest Skipped instead of failing the build. Both anchor at the refused
     * method's own name. The
     * diagnostic is deduped per (method, reason); the demotion never is.
     */
    private void reportRefusal(MethodGraph.MethodInfo info, String reason) {
        graph.reportRefusal(info, reason);
    }

    /**
     * A translation refusal whose diagnostic was already emitted, positioned
     * at the offending expression (some refusals report at the expression
     * rather than the enclosing method). The catch above must not re-report.
     */
    static final class ReportedViolation extends JavaViolationException {
        ReportedViolation() {
            super("already reported");
        }
    }

    /** The {@code @Verify} annotation on an element's RuntimeInvisibleAnnotations, or null. */
    private static java.lang.classfile.Annotation verifyAnnotation(
            java.lang.classfile.AttributedElement element) {
        return ClassResolver.verifyAnnotation(element);
    }

    private static boolean verifyValue(java.lang.classfile.Annotation verify, String element,
                                       boolean defaultValue) {
        return ClassResolver.verifyValue(verify, element, defaultValue);
    }

    private boolean isOptedOut(java.lang.classfile.ClassModel cm, java.lang.classfile.MethodModel m) {
        return resolver.isOptedOut(cm, m);
    }

    // --- Statements ---

    // --- Switch desugaring (arrow switches -> if cascade) ---

    // --- Loops ---

    // --- Expressions ---

    // --- Casts ---

    // --- Calls ---



    // =====================================================================
    // Per-method translation: bytecode -> Laurel via BodyTranslator (expressions)
    // and ControlFlowStructurer (control flow).
    // =====================================================================

    /**
     * The classfile method behind a walked declaration: the {@code
     * ordinal}-th same-named non-synthetic, non-bridge method with a Code
     * attribute (source order matches classfile order).
     */
    private static java.lang.classfile.MethodModel findMethodModel(
            java.lang.classfile.ClassModel cm, String name, int ordinal) {
        int seen = 0;
        for (var m : cm.methods()) {
            if (!m.methodName().stringValue().equals(name)) {
                continue;
            }
            if ((m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                    | java.lang.classfile.ClassFile.ACC_BRIDGE)) != 0) {
                continue;
            }
            if (m.code().isEmpty()) {
                continue;
            }
            if (seen++ == ordinal) {
                return m;
            }
        }
        return null;
    }

    private TranslationContext translationCtx;

    private TranslationContext translationContext() {
        if (translationCtx == null) {
            translationCtx = new TranslationContext(reporter, src, lf, resolver, composites, graph);
            translationCtx.contracts = contracts;
        }
        return translationCtx;
    }

    /**
     * One direct body run: the structured top-level statements + the side
     * tables. A conversion refusal is CAPTURED with the partial body (its
     * spec markers drive the contract refusal pass): contract-clause
     * refusals beat body-statement refusals.
     */
    private record BodyRun(List<AstNode<StmtExpr>> topStmts, BodyTranslator body, boolean structured,
                             JavaViolationException refusal) {
    }

    private BodyTranslator newBodyTranslator(java.lang.classfile.ClassModel cm,
                                     org.strata.java.bytecode.frontend.MethodBody shape,
                                     TranslationContext ctx) {
        BodyTranslator b = new BodyTranslator(ctx, shape.slotName(), shape.slotDesc(), shape.declared());
        b.locals = shape.locals();
        b.cm = cm;
        b.crt = shape.crt();
        b.lines = shape.lines();
        b.seqToBci = shape.seqToBci();
        b.slotTypeAnnos = shape.slotAnnos();
        b.impureLocals = shape.impureLocals();
        b.retDesc = shape.retDesc();
        b.isInstance = !shape.isStatic();
        b.inCtor = shape.isCtor();
        var loaded = new java.util.HashSet<Integer>();
        for (var el : shape.seq()) {
            if (el instanceof java.lang.classfile.instruction.LoadInstruction li) {
                loaded.add(li.slot());
            } else if (el instanceof java.lang.classfile.instruction.IncrementInstruction ii) {
                loaded.add(ii.slot());
            }
        }
        b.loadedSlots = loaded;
        collectRangeCastAnnotations(cm, shape, b);
        return b;
    }

    /** The method whose body is being run, so its CAST type annotations can be read. */
    private java.lang.classfile.MethodModel bodyMethod;

    /** CAST type annotations of {@link #bodyMethod}, keyed by the bci they annotate. */
    private void collectRangeCastAnnotations(java.lang.classfile.ClassModel cm,
                                             org.strata.java.bytecode.frontend.MethodBody shape,
                                             BodyTranslator b) {
        var m = bodyMethod;
        var code = m == null ? null : m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return;
        }
        var elements = new ArrayList<java.lang.classfile.CodeElement>();
        code.forEach(elements::add);
        int[] bciOf = org.strata.java.bytecode.frontend.MethodBodyFactory.bciTable(elements);
        var labels = new HashMap<java.lang.classfile.Label, Integer>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof java.lang.classfile.instruction.LabelTarget lt) {
                labels.put(lt.label(), bciOf[i]);
            }
        }
        for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(code)) {
            // A CAST entry's target is a TypeArgumentTarget (offset + type-argument index),
            // not an OffsetTarget.
            if (ta.targetInfo() instanceof java.lang.classfile.TypeAnnotation.TypeArgumentTarget ot
                    && ta.targetInfo().targetType() == java.lang.classfile.TypeAnnotation.TargetType.CAST) {
                Integer bci = labels.get(ot.target());
                if (bci != null) {
                    b.rangeCastAnnotations.computeIfAbsent(bci, k -> new java.util.HashSet<>())
                            .add(java.lang.constant.ClassDesc.ofDescriptor(
                                    ta.annotation().className().stringValue()).displayName());
                    if (org.strata.java.bytecode.frontend.ClassfileQueries.isImpure(ta.annotation())) {
                        b.impureCasts.add(bci);
                    }
                }
            }
        }
    }

    /**
     * Structured when the CFG has real control flow, rolling back to the
     * linear form on a structural bail. A JavaViolationException is a
     * TRANSLATION refusal and propagates — a refusal on structured code must
     * refuse the method, never silently retry it linearly.
     */
    private BodyRun runBody(java.lang.classfile.ClassModel cm,
                                org.strata.java.bytecode.frontend.MethodBody shape,
                                TranslationContext ctx) {
        if (shape.dom() != null) {
            BodyTranslator sbody = newBodyTranslator(cm, shape, ctx);
            try {
                var structurer = new ControlFlowStructurer(shape.cfg(), shape.dom(), sbody, ctx);
                return new BodyRun(structurer.structure(), sbody, true, null);
            } catch (org.strata.java.bytecode.frontend.StructException e) {
                // fall through to the linear form
            } catch (JavaViolationException e) {
                return new BodyRun(null, sbody, true, e);
            }
        }
        BodyTranslator body = newBodyTranslator(cm, shape, ctx);
        try {
            body.run(shape.seq());
        } catch (JavaViolationException e) {
            return new BodyRun(null, body, false, e);
        }
        return new BodyRun(new ArrayList<>(body.stmts), body, false, null);
    }

    /**
     * The contract-clause refusal side effects of a method whose BODY
     * translation already refused: contract clauses rank before body
     * statements (the header is converted first), so a header clause's
     * refusal must win over the captured body refusal.
     */
    private void contractRefusalPass(BodyTranslator body, TranslationContext ctx) {
        var specs = new ArrayList<Map.Entry<AstNode<StmtExpr>, BodyTranslator.SpecStmt>>(
                body.specStmts.entrySet());
        specs.sort(java.util.Comparator.comparingInt(e -> e.getValue().seqIdx()));
        for (var e : specs) {
            if (e.getValue().name().equals("invariant")) {
                throw new JavaViolationException("invariants are not allowed in a method");
            }
        }
        for (String kind : new String[]{"precondition", "postcondition"}) {
            for (var e : specs) {
                var spec = e.getValue();
                if (!spec.name().equals(kind)) {
                    continue;
                }
                if (spec.args().size() != 1) {
                    throw new JavaViolationException(
                            "A call to '" + kind + "' may have only one argument");
                }
                if (spec.lambdaArg() != null) {
                    if (kind.equals("postcondition")) {
                        var binders = body.lambdaBinders(spec.lambdaArg());
                        body.translateLambdaBody(spec.lambdaArg(),
                                binders != null && binders.size() == 1
                                        ? Map.of(binders.get(0).name(), LAUREL_RESULT_BINDING)
                                        : Map.of());
                    } else {
                        body.translateLambdaBody(spec.lambdaArg(), Map.of());
                    }
                } else {
                    body.checkContractArg(spec.args().get(0));
                }
            }
        }
        for (var e : specs) {
            var spec = e.getValue();
            if (!spec.name().equals("modifies") || spec.args().isEmpty()) {
                continue;
            }
            var arg = spec.args().get(0);
            var argSpec = body.specStmts.get(arg);
            if (argSpec == null || !argSpec.name().equals("everything")) {
                body.checkContractArg(arg);
            }
        }
        for (var e : specs) {
            var spec = e.getValue();
            if (!spec.name().equals("throwsOn")) {
                continue;
            }
            if (ctx.method.throwsJoin == null) {
                throw new JavaViolationException(
                        "throwsOn requires the method to declare the thrown type in its throws clause");
            }
            body.checkPlaceable(spec.args().get(0));
            for (var link : spec.links()) {
                if (link.name().equals("postcondition")) {
                    convertCasePostcondition(link, body, UNKNOWN_RANGE, ctx);
                } else {
                    var argSpec = body.specStmts.get(link.arg());
                    if (argSpec == null || !argSpec.name().equals("everything")) {
                        body.checkPlaceable(link.arg());
                    }
                }
            }
        }
    }

    /** The hoisted local declarations as Laurel statements at the method top. */
    private List<AstNode<StmtExpr>> hoistedDeclStmts(
            org.strata.java.bytecode.frontend.MethodBody shape,
            BodyTranslator body, TranslationContext ctx) {
        var out = new ArrayList<AstNode<StmtExpr>>();
        for (var h : shape.hoisted()) {
            LaurelFactory.refuseReservedName(h.name());
            TypeRef t = h.genericType() != null
                    ? TypeRef.ofRendered(h.genericType(), false, false)
                    : TypeRef.ofDescriptor(h.desc(), false, false);
            var param = new ParameterOpt(ident(h.name()),
                    Optional.of(node(t.toLaurel(ctx.typeParams, ctx.methodTypeParams,
                            resolver, composites))));
            StmtExpr decl = new StmtExpr.Var(new Variable.Declare(param));
            FileRange range = body.stmtRange(h.seqIdx(), false);
            out.add(ctx.placeStatement(body.e(decl), range));
        }
        return out;
    }

    /** An element carries the given simple-named annotation (visible or invisible). */
    private static boolean hasAnnotation(java.lang.classfile.AttributedElement el,
                                               String simpleName) {
        return Declarations.hasAnnotation(el, simpleName);
    }

    /** A spec marker outside the contract header refuses. */
    private void checkNoSpecMarkers(AstNode<StmtExpr> n, BodyTranslator body) {
        if (body.specStmts.containsKey(n)) {
            throw new JavaViolationException(
                    "call to contract method must come before the rest of the body");
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            checkNoSpecMarkers(c, body);
        }
    }

    private boolean onlySelfWrites(List<AstNode<StmtExpr>> implementation, BodyTranslator body) {
        for (var stmt : implementation) {
            if (!onlySelfWrites(stmt, body)) {
                return false;
            }
        }
        return true;
    }

    private boolean onlySelfWrites(AstNode<StmtExpr> n, BodyTranslator body) {
        switch (n.val()) {
            case StmtExpr.Assert a -> {
                return true;
            }
            case StmtExpr.Assume a -> {
                return true;
            }
            case StmtExpr.Assign a -> {
                for (var t : a.targets()) {
                    if (t.val() instanceof Variable.Field fld && !isSelf(fld.target())) {
                        return false;
                    }
                }
            }
            case StmtExpr.CompoundAssign c -> {
                if (c.target().val() instanceof Variable.Field fld && !isSelf(fld.target())) {
                    return false;
                }
            }
            case StmtExpr.IncrDecr d -> {
                if (d.target().val() instanceof Variable.Field fld && !isSelf(fld.target())) {
                    return false;
                }
            }
            case StmtExpr.InstanceCall c when translationContext().constructorCalls.contains(c.callee().text()) -> {
                if (!isSelf(c.target())) {
                    return false;
                }
            }
            default -> {
            }
        }
        String key = body.calleeKeys.get(n.val());
        if (key != null && !decls.isTransparent(key) && !contracts.isFrameEmptyKey(key) && !calleeDeclaresEmptyFrame(body, n)) {
            return false;
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            if (!onlySelfWrites(c, body)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSelf(AstNode<StmtExpr> n) {
        return n.val() instanceof StmtExpr.Var(Variable.Local l)
                && l.name().text().equals(LAUREL_SELF_BINDING);
    }

    private List<Condition> constructorFieldFacts(List<AstNode<StmtExpr>> implementation,
                                                  List<org.strata.jverify.laurel.Parameter> inputs) {
        var params = new java.util.HashSet<String>();
        for (var p : inputs) {
            params.add(p.name().text());
        }
        var stores = new java.util.LinkedHashMap<String, AstNode<StmtExpr>>();
        var assignedTwice = new java.util.HashSet<String>();
        for (var stmt : implementation) {
            if (stmt.val() instanceof StmtExpr.Assign a && a.targets().size() == 1
                    && a.targets().get(0).val() instanceof Variable.Field fld && isSelf(fld.target())) {
                if (stores.put(fld.fieldName().text(), a.value()) != null) {
                    assignedTwice.add(fld.fieldName().text());
                }
                continue;
            }
            if (stmt.val() instanceof StmtExpr.Assert || stmt.val() instanceof StmtExpr.Assume
                    || (stmt.val() instanceof StmtExpr.InstanceCall c && isSelf(c.target())
                            && translationContext().constructorCalls.contains(c.callee().text()))) {
                continue;
            }
            return List.of();
        }
        var facts = new ArrayList<Condition>();
        for (var e : stores.entrySet()) {
            if (assignedTwice.contains(e.getKey()) || !simpleValue(e.getValue(), params)) {
                continue;
            }
            var read = noRange(new StmtExpr.Var(new Variable.Field(
                    noRange(new StmtExpr.Var(new Variable.Local(inputs.get(0).name()))), ident(e.getKey()))));
            var eq = primitiveOp(new Operation.Eq(), read, e.getValue());
            facts.add(new Condition(noRange(eq), Optional.empty(), new ConditionMode.Both()));
        }
        return facts;
    }

    private static boolean simpleValue(AstNode<StmtExpr> n, java.util.Set<String> params) {
        switch (n.val()) {
            case StmtExpr.LiteralInt l -> {
                return true;
            }
            case StmtExpr.LiteralBool l -> {
                return true;
            }
            case StmtExpr.Var(Variable.Local l) -> {
                return params.contains(l.name().text());
            }
            case StmtExpr.Var(Variable.Field f) -> {
                return !isSelf(f.target()) && simpleValue(f.target(), params);
            }
            case StmtExpr.StaticCall p when p.callee().text().startsWith("$") -> {
                for (var a : p.arguments()) {
                    if (!simpleValue(a, params)) {
                        return false;
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean calleeDeclaresEmptyFrame(BodyTranslator body, AstNode<StmtExpr> n) {
        var ref = body.calleeModels.get(n.val());
        if (ref == null || ref.method().code().isPresent()) {
            return false;
        }
        boolean defaultFrameEmpty = !ref.method().methodTypeSymbol().returnType().descriptorString().equals("V");
        var contract = decls.sourceContracts.get(ref.declaringFqn());
        if (contract == null) {
            return defaultFrameEmpty;
        }
        for (var cm : contract.cm().methods()) {
            if (cm.methodName().stringValue().equals(ref.method().methodName().stringValue())
                    && cm.methodTypeSymbol().equals(ref.method().methodTypeSymbol())) {
                var code = cm.code().orElse(null);
                if (code == null) {
                    return defaultFrameEmpty;
                }
                for (var el : code) {
                    if (el instanceof java.lang.classfile.instruction.InvokeInstruction inv
                            && (inv.owner().asInternalName().equals("org/strata/jverify/JVerify")
                                    || inv.owner().asInternalName().equals("org/strata/jverify/JVerify$ThrowsOnCase"))
                            && inv.name().stringValue().equals("modifies")) {
                        return false;
                    }
                }
                return defaultFrameEmpty;
            }
        }
        return defaultFrameEmpty;
    }

    /** bodyPotentiallyWritesHeap over the emitted Laurel implementation. */
    private boolean writesHeap(List<AstNode<StmtExpr>> implementation, BodyTranslator body) {
        for (var stmt : implementation) {
            if (heapNode(stmt, body)) {
                return true;
            }
        }
        return false;
    }

    private boolean heapNode(AstNode<StmtExpr> n, BodyTranslator body) {
        switch (n.val()) {
            // A spec describes the method, not its runtime effect:
            // check/assume subtrees don't count.
            case StmtExpr.Assert a -> {
                return false;
            }
            case StmtExpr.Assume a -> {
                return false;
            }
            case StmtExpr.Assign a -> {
                for (var t : a.targets()) {
                    if (t.val() instanceof Variable.Field) {
                        return true;
                    }
                }
            }
            case StmtExpr.CompoundAssign c -> {
                if (c.target().val() instanceof Variable.Field) {
                    return true;
                }
            }
            case StmtExpr.IncrDecr d -> {
                if (d.target().val() instanceof Variable.Field) {
                    return true;
                }
            }
            case StmtExpr.While w -> {
                // A loop whose body allocates havocs the whole heap, which an
                // empty frame cannot cover; an array allocation has no
                // constructor call to count as the write.
                if (allocatesArray(n)) {
                    return true;
                }
            }
            default -> {
            }
        }
        String key = body.calleeKeys.get(n.val());
        if (key != null && !decls.isTransparent(key) && !contracts.isFrameEmptyKey(key) && !calleeDeclaresEmptyFrame(body, n)) {
            return true;
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            if (heapNode(c, body)) {
                return true;
            }
        }
        return false;
    }

    /** {@code n}'s subtree allocates a JArray. */
    private static boolean allocatesArray(AstNode<StmtExpr> n) {
        if (n.val() instanceof StmtExpr.New nw && nw.ref().text().equals(ArrayModel.SORT)
                && !nw.typeArgs().isEmpty()) {
            return true;
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            if (allocatesArray(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JVM bytecode-verification gate: when enabled ({@code jvmVerifyInput}),
     * every input classfile (top-level and nested) must pass
     * {@link ClassFile#verify} before translation. It is meant for classfiles
     * this frontend did not compile itself (a non-javac jar or classfile,
     * which may not verify); javac output always verifies, so a caller that
     * ran javac itself may skip it. Bytecode that fails JVM verification never executes
     * on a JVM, so a Strata verdict about it would be meaningless -- and the
     * operand-stack simulation downstream assumes verifiable input (balanced
     * stack, consistent types at merge points).
     *
     * <p>Reports one error per genuine {@code VerifyError}, with the
     * verifier's own message text, and returns true when the input must be
     * refused. Bytecode whose type hierarchy this frontend cannot fully
     * resolve is NOT refused: an unresolvable supertype means our classpath
     * is incomplete, not that the bytecode is defective, and we cannot
     * soundly verify what we cannot resolve. The decision is driven by our
     * own resolver (did a lookup come up empty or throw?), never by the
     * verifier's message wording, and is scoped per METHOD: a class whose
     * whole-class verification saw an unresolvable type is re-judged one
     * method at a time, so a missing dependency in one method (or class)
     * never suppresses a genuine defect in another. The downstream
     * {@link ClassResolver} remains the authority on missing dependencies
     * actually used.
     */
    private boolean reportUnverifiableClassfiles(Collection<URI> sourceUris, List<ClassSource> classes) {
        if (classes.isEmpty()) {
            return false;   // nothing to verify (and nothing will be translated)
        }
        URI firstUri = sourceUris.stream().findFirst().orElse(null);
        boolean refused = false;
        URLClassLoader loader = classPathLoader();
        try {
            ClassHierarchyResolver base = hierarchyResolver(classes, loader);
            for (var cls : classes) {
                URI clsUri = sourceUriFor(cls, sourceUris);
                URI anchor = clsUri != null ? clsUri : firstUri;
                for (ClassModel cm : allModels(cls)) {
                    String className = JavaNames.dottedFqn(cm.thisClass().asInternalName());
                    // Pre-Java-6 bytecode (major < 50) uses the old inference
                    // verifier, which java.lang.classfile does not implement:
                    // verify() would return a confusing "Inference verification
                    // is not supported" as if the bytecode were defective.
                    // Refuse explicitly on the version instead (a structured
                    // classfile field, not a message string), and skip verify.
                    if (cm.majorVersion() < ClassFile.JAVA_6_VERSION) {
                        reporter.reportError(anchor, Reporter.NO_RANGE,
                                "class " + className + " has an unsupported class file version ("
                                        + cm.majorVersion() + "): pre-Java-6 bytecode cannot be"
                                        + " verified (recompile with a modern javac)");
                        refused = true;
                        continue;
                    }
                    // Fast path: verify the whole class at once. If nothing
                    // was unresolvable, its findings are trustworthy.
                    Judgment whole = judge(cf -> cm, base);
                    if (!whole.incompleteHierarchy()) {
                        for (String message : whole.errors()) {
                            reporter.reportError(anchor, Reporter.NO_RANGE,
                                    "input failed JVM bytecode verification: " + message);
                            refused = true;
                        }
                        continue;
                    }
                    // Some lookup in THIS class came up empty (or threw --
                    // an unlinkable classpath jar). Never silent: the user
                    // must see that this class was not (fully) verified.
                    reporter.reportWarning(anchor, Reporter.NO_RANGE,
                            "class " + className + " was not fully JVM-verified: its class"
                                    + " hierarchy could not be resolved (types missing from the"
                                    + " classpath)");
                    // verify(cm) checks all methods in one call, so the
                    // combined error list cannot be trusted -- the
                    // unresolvable method's noise and a sibling method's
                    // genuine defect are indistinguishable. Judge each method
                    // independently (a singleton copy of the class with the
                    // other methods dropped): a method whose own hierarchy is
                    // incomplete is tolerated, a method with a genuine defect
                    // still refuses. Deduped because class-level (non-method)
                    // findings repeat in every singleton.
                    var uniqueErrors = new java.util.LinkedHashSet<String>();
                    for (var method : cm.methods()) {
                        Judgment single = judge(cf -> onlyMethod(cf, cm, method), base);
                        if (!single.incompleteHierarchy()) {
                            uniqueErrors.addAll(single.errors());
                        }
                    }
                    for (String message : uniqueErrors) {
                        reporter.reportError(anchor, Reporter.NO_RANGE,
                                "input failed JVM bytecode verification: " + message);
                        refused = true;
                    }
                }
            }
        } finally {
            // Resource cleanup only; a close() failure must not affect the
            // verdict (mirrors the surrounding refusal-not-crash contract).
            try {
                loader.close();
            } catch (java.io.IOException ignored) {
                // best-effort close
            }
        }
        return refused;
    }

    /** One verification pass's outcome: the findings, and whether they can be trusted. */
    private record Judgment(boolean incompleteHierarchy, List<String> errors) {
    }

    /**
     * Verify {@code view.apply(cm)} with a resolver that trips an
     * incomplete-hierarchy flag when any lookup returns null or throws (an
     * unlinkable classpath jar). The flag scopes exactly to this pass, so a
     * caller can judge a whole class or a single method independently. A
     * throw from the view transform or from {@code verify} itself counts as
     * incomplete when the flag tripped (the singleton re-emission regenerates
     * stack maps, which needs the same hierarchy answers); otherwise it is a
     * genuine finding (e.g. a malformed this_class name, which ClassDesc
     * rejects) -- refused, never a crash.
     */
    private static Judgment judge(java.util.function.Function<ClassFile, ClassModel> view,
                                  ClassHierarchyResolver base) {
        boolean[] incomplete = {false};
        ClassHierarchyResolver resolver = desc -> {
            try {
                var info = base.getClassInfo(desc);
                if (info == null) {
                    incomplete[0] = true;
                }
                return info;
            } catch (RuntimeException | LinkageError e) {
                incomplete[0] = true;
                return null;
            }
        };
        var cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver));
        List<String> errors = new ArrayList<>();
        try {
            for (VerifyError err : cf.verify(view.apply(cf))) {
                errors.add(sanitize(err.getMessage()));
            }
        } catch (RuntimeException | LinkageError e) {
            if (!incomplete[0]) {
                errors.add(e.getMessage() != null
                        ? sanitize(e.getMessage()) : e.getClass().getSimpleName());
            }
        }
        return new Judgment(incomplete[0], errors);
    }

    /**
     * The JDK verifier wraps internal exceptions into {@code VerifyError}s
     * whose message leads with the exception's class name (e.g.
     * "java.lang.classfile.constantpool.ConstantPoolException: Bad CP
     * index..."). Strip that leaked implementation detail; the description
     * after it is the user-relevant part.
     */
    private static String sanitize(String message) {
        if (message == null) {
            return "(no detail)";
        }
        return message.replaceFirst(
                "^(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error): ", "");
    }

    /**
     * A copy of {@code cm} with every method except {@code keep} dropped,
     * re-emitted through {@code cf} so the singleton's stack-map regeneration
     * consults the same (recording) hierarchy resolver as the verification.
     */
    private static ClassModel onlyMethod(ClassFile cf, ClassModel cm,
                                         java.lang.classfile.MethodModel keep) {
        byte[] singleton = cf.transformClass(cm,
                java.lang.classfile.ClassTransform.dropping(
                        ce -> ce instanceof java.lang.classfile.MethodModel mm && mm != keep));
        return ClassFile.of().parse(singleton);
    }

    /** A class and its nested classfiles, for whole-unit checks. */
    private static List<ClassModel> allModels(ClassSource cls) {
        var models = new ArrayList<ClassModel>(1 + cls.nestedModels().size());
        models.add(cls.classModel());
        models.addAll(cls.nestedModels());
        return models;
    }

    /**
     * Class-hierarchy knowledge for the JVM verifier's assignability checks:
     * program classes answer from their pooled classfiles (they are not
     * loadable -- they live in a temp dir); everything else (JCL, the spec
     * library, javac-classpath jars) resolves by class loading without
     * initialization. A type that neither layer resolves comes back null (or,
     * for an unlinkable classpath jar, throws) -- the per-class caller treats
     * that as an incomplete hierarchy and does not refuse the class (see
     * {@link #reportUnverifiableClassfiles}).
     */
    private static ClassHierarchyResolver hierarchyResolver(List<ClassSource> classes,
                                                            ClassLoader loader) {
        Map<ClassDesc, ClassDesc> superclasses = new HashMap<>();
        var interfaces = new ArrayList<ClassDesc>();
        for (var cls : classes) {
            for (ClassModel cm : allModels(cls)) {
                // A malformed this_class/superclass name is skipped here so
                // ClassDesc's own validation cannot crash the gate before
                // verify() runs. A sibling that references the skipped class
                // then sees an unresolved lookup and is tolerated per class --
                // the verifier still flags the malformed class on its own turn.
                ClassDesc desc = classDescOrNull(cm.thisClass().asInternalName());
                if (desc == null) {
                    continue;
                }
                if (cm.flags().has(AccessFlag.INTERFACE)) {
                    interfaces.add(desc);
                } else {
                    cm.superclass()
                            .map(sup -> classDescOrNull(sup.asInternalName()))
                            .ifPresent(sup -> superclasses.put(desc, sup));
                }
            }
        }
        return ClassHierarchyResolver.of(interfaces, superclasses)
                .orElse(ClassHierarchyResolver.ofClassLoading(loader))
                .cached();
    }

    /** {@link ClassDesc#ofInternalName} or null if the name is malformed (rejected by ClassDesc). */
    private static ClassDesc classDescOrNull(String internalName) {
        try {
            return ClassDesc.ofInternalName(internalName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The javac classpath as a loader for hierarchy resolution, parented on
     * the loader {@link ClassResolver} itself resolves through, so the gate's
     * type universe matches the rest of the compiler's (a mismatch could
     * refuse input the compiler resolves fine under embedded/nested-loader
     * use).
     */
    private URLClassLoader classPathLoader() {
        var urls = new ArrayList<URL>(classPath.size());
        for (var p : classPath) {
            try {
                // toUri() yields a directory URI with the trailing slash
                // URLClassLoader needs to treat it as a directory, not a jar.
                urls.add(p.toUri().toURL());
            } catch (java.net.MalformedURLException e) {
                // A file: URI from a Path effectively never malforms; a
                // dropped entry only makes a type unresolvable, which is
                // tolerated (filtered), never a false refusal.
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new),
                BytecodeToLaurelCompiler.class.getClassLoader());
    }

    /**
     * Report the detected input tier ONCE per run, so degraded feedback is
     * never a surprise: FULL is silent, DEBUG/BARE warn about the precision
     * loss, and an input with no debug information at all refuses (its
     * feedback would carry no source positions). Returns true when the
     * input is refused.
     */
    private boolean reportInputTier(Collection<URI> sourceUris, List<ClassSource> classes) {
        if (classes.isEmpty()) {
            return false;   // nothing to classify (and nothing will be verified)
        }
        var facts = org.strata.java.bytecode.frontend.CompilationFacts.NONE_OBSERVED;
        for (var cls : classes) {
            facts = facts.or(org.strata.java.bytecode.frontend.CompilationFacts.of(cls.classModel()));
            for (var nested : cls.nestedModels()) {
                facts = facts.or(org.strata.java.bytecode.frontend.CompilationFacts.of(nested));
            }
        }
        URI anchor = sourceUris.stream().findFirst().orElse(null);
        switch (facts.tier()) {
            case FULL -> { }
            case DEBUG -> {
                if (!tierIsSelfInflicted) {
                    reporter.reportWarning(anchor, Reporter.NO_RANGE,
                            "input compiled without -Xjcov (no statement spans): diagnostics carry"
                                    + " method precision only");
                }
            }
            case BARE -> reporter.reportWarning(anchor, Reporter.NO_RANGE,
                    "input compiled without -g (no local-variable debug info): diagnostics carry"
                            + " method precision only and locals get synthetic names");
            case NONE -> {
                reporter.reportError(anchor, Reporter.NO_RANGE,
                        "input carries no debug information (compiled with -g:none or stripped):"
                                + " verification feedback would have no source positions; recompile"
                                + " with -g -Xjcov (best), -g, or default javac flags");
                return true;
            }
        }
        return false;
    }

    private void recordStatus(FileRange range, MethodVerificationStatus status) {
        if (src.uri == null) {
            return;
        }
        var tree = methodStatuses.computeIfAbsent(src.uri, k -> new IntervalTree<>());
        if (range == UNKNOWN_RANGE) {
            tree.insert(0, 0, status);
            return;
        }
        var posRange = positionRange(range);
        tree.insert(posRange.start().line(), posRange.end().line(), status);
    }

    /** A case's {@code .postcondition(...)} property. */
    private Condition convertCasePostcondition(BodyTranslator.SpecLink link, BodyTranslator body,
                                                     FileRange range, TranslationContext ctx) {
        var handle = link.lambdaArg();
        if (handle == null) {
            // A functional-interface cast may wrap the lambda; unwrap it.
            handle = body.castWrappedLambdas.get(link.arg());
        }
        var binders = handle != null ? body.lambdaBinders(handle) : null;
        if (binders == null || binders.size() != 1) {
            throw new JavaViolationException(
                    "the argument to a throwsOn case's postcondition must be a one-parameter lambda");
        }
        String desc = binders.get(0).desc();
        String paramFqn = desc.startsWith("L")
                ? JavaNames.dottedFqn(desc.substring(1, desc.length() - 1)) : null;
        if (paramFqn == null || !paramFqn.equals(ctx.method.throwsJoin)) {
            throw new JavaViolationException(
                    "a throwsOn case's postcondition parameter must be typed at the method's declared throws type"
                            + (ctx.method.throwsJoin != null ? " ('" + ctx.method.throwsJoin + "')" : "")
                            + "; narrow with instanceof and a cast inside the property");
        }
        var converted = body.coerce(ArrayAccessTranslator.stripLoadFacts(
                body.translateLambdaBody(handle, Map.of(binders.get(0).name(), THROWN_BINDING))), "Z");
        return new Condition(LaurelTrees.rerange(converted, range, ctx.method.statementNodes),
                Optional.empty(), new ConditionMode.Both());
    }

    /**
     * A program {@code modifies} target: an array-typed argument ({@code
     * original}'s descriptor) may change only its contents,
     * {@code x#contentsVersion}; any other target is {@code placed} as is.
     */
    private AstNode<StmtExpr> modifiesTarget(AstNode<StmtExpr> original, AstNode<StmtExpr> placed,
                                             BodyTranslator body) {
        if (ArrayModel.kindOfDescriptor(body.descOf.get(original)) != null && composites.arrays.enabled()) {
            return ArrayModel.frameTarget(placed);
        }
        return placed;
    }

    /**
     * Report every floating-point remainder in {@code shape} at its own position.
     * {@code DREM}/{@code FREM} are
     * distinct opcodes, so the construct survives erasure intact -- nothing about
     * this check is source-only. Marks the method so the signature refusal that
     * follows demotes it without a second, vaguer diagnostic.
     */
    private void reportFloatingPointModulo(org.strata.java.bytecode.frontend.MethodBody shape,
                                           FileRange methodAnchor,
                                           MethodGraph.MethodInfo info) {
        var seq = shape.seq();
        for (int i = 0; i < seq.size(); i++) {
            if (!(seq.get(i) instanceof java.lang.classfile.Instruction in)
                    || (in.opcode() != java.lang.classfile.Opcode.DREM
                            && in.opcode() != java.lang.classfile.Opcode.FREM)) {
                continue;
            }
            var span = org.strata.java.bytecode.frontend.MethodBodyFactory
                    .spanOf(shape.crt(), shape.seqToBci(), i);
            FileRange at = span.isNone()
                    ? methodAnchor
                    : src.rangeOf(span.beginLine(), span.beginCol(), span.endLine(), span.endCol());
            if (!info.quiet) {
                reporter.reportError(src.uri, positionRange(at),
                        "modulo operator (%) with floating-point types is not supported");
            }
            info.constructErrorReported = true;
        }
    }

    /**
     * The type of an instance procedure's synthesized {@code self} parameter. For a
     * GENERIC composite this must be the instantiation over the composite's own type
     * parameters ({@code Applied(C, [U])}), not the raw sort: MonomorphizeComposites
     * replaces the generic declaration with one instantiation per use site, so a
     * {@code self} typed at the raw {@code C} refers to a sort that no longer exists
     * -- and every field read through it fails with it
     * (`Resolution failed: 'value' is not defined`, NestedPolymorphism).
     */
    private static Identifier withRange(Identifier id, FileRange range) {
        return range == UNKNOWN_RANGE ? id : new Identifier(id.text(), id.uniqueId(), range);
    }

    private HighType selfType(String classFqn) {
        var params = resolver.classTypeParams(classFqn);
        var base = node(new HighType.UserDefined(ident(classFqn)));
        if (params.isEmpty()) {
            return base.val();
        }
        return new HighType.Applied(base,
                params.stream().map(p -> node((HighType) new HighType.TVar(ident(p)))).toList());
    }

    private static boolean contractBodyHasStatements(java.lang.classfile.MethodModel m) {
        var code = m.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var el : code) {
            if (el instanceof java.lang.classfile.instruction.StoreInstruction
                    || el instanceof java.lang.classfile.instruction.ArrayStoreInstruction
                    || (el instanceof java.lang.classfile.instruction.FieldInstruction fi
                            && (fi.opcode() == java.lang.classfile.Opcode.PUTFIELD
                                    || fi.opcode() == java.lang.classfile.Opcode.PUTSTATIC))) {
                return true;
            }
        }
        return false;
    }

    private boolean overridesAbstractPrecondition(String classFqn, java.lang.classfile.MethodModel m) {
        String name = m.methodName().stringValue();
        var params = m.methodTypeSymbol().parameterList();
        var queue = new java.util.ArrayDeque<String>();
        String sup = resolver.programSuper.get(classFqn);
        if (sup != null) {
            queue.add(sup);
        }
        queue.addAll(resolver.directInterfacesOf(classFqn));
        var seen = new java.util.HashSet<String>();
        while (!queue.isEmpty()) {
            String t = queue.poll();
            var cm = resolver.programClassModels.get(t);
            if (cm == null || !seen.add(t)) {
                continue;
            }
            for (var bm : cm.methods()) {
                if (bm.methodName().stringValue().equals(name) && bm.methodTypeSymbol().parameterList().equals(params)) {
                    return invokesIsAbstract(bm);
                }
            }
            String s2 = resolver.programSuper.get(t);
            if (s2 != null) {
                queue.add(s2);
            }
            queue.addAll(resolver.directInterfacesOf(t));
        }
        return false;
    }

    private static boolean invokesIsAbstract(java.lang.classfile.MethodModel m) {
        var code = m.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var el : code) {
            if (el instanceof java.lang.classfile.instruction.InvokeInstruction inv
                    && inv.owner().asInternalName().equals("org/strata/jverify/JVerify")
                    && (inv.name().stringValue().equals("isAbstract")
                            || inv.name().stringValue().equals("isAbstractBoolean"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the callee identifier of every InstanceCall/StaticCall in {@code
     * n}'s subtree to {@code out}; an array wrapper also adds the JArray
     * member it calls.
     */
    private static void collectCalleeNames(AstNode<StmtExpr> n, java.util.Set<String> out) {
        switch (n.val()) {
            case StmtExpr.InstanceCall c -> out.add(c.callee().text());
            case StmtExpr.StaticCall c -> {
                out.add(c.callee().text());
                String member = ArrayModel.wrappedMember(c.callee().text());
                if (member != null) {
                    out.add(member);
                }
            }
            default -> {
            }
        }
        for (var child : BodyTranslator.childrenOf(n.val())) {
            collectCalleeNames(child, out);
        }
    }

    /**
     * True iff formal parameter {@code index} of {@code m} carries a
     * {@code Nullable} annotation: a type annotation at any type path, or a
     * declaration annotation.
     */
    private static boolean nullableParameter(java.lang.classfile.MethodModel m, int index) {
        for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(m)) {
            if (ta.targetInfo() instanceof java.lang.classfile.TypeAnnotation.FormalParameterTarget fp
                    && fp.formalParameterIndex() == index
                    && ArrayModel.isNullableAnnotation(ta.annotation().className().stringValue())) {
                return true;
            }
        }
        var perParameter = new ArrayList<List<List<java.lang.classfile.Annotation>>>();
        m.findAttribute(java.lang.classfile.Attributes.runtimeVisibleParameterAnnotations())
                .ifPresent(a -> perParameter.add(a.parameterAnnotations()));
        m.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .ifPresent(a -> perParameter.add(a.parameterAnnotations()));
        for (var params : perParameter) {
            if (index < params.size() && params.get(index).stream()
                    .anyMatch(a -> ArrayModel.isNullableAnnotation(a.className().stringValue()))) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code m}'s return type carries a {@code Nullable} type annotation (any type path) or declaration annotation. */
    private static boolean nullableReturn(java.lang.classfile.MethodModel m) {
        for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(m)) {
            if (ta.targetInfo().targetType() == java.lang.classfile.TypeAnnotation.TargetType.METHOD_RETURN
                    && ArrayModel.isNullableAnnotation(ta.annotation().className().stringValue())) {
                return true;
            }
        }
        return hasNullableDeclarationAnnotation(m);
    }

    /** A procedure signature: Laurel inputs, outputs, and the class-typed return sort (or null). */
    private record Signature(List<org.strata.jverify.laurel.Parameter> inputs,
                             List<org.strata.jverify.laurel.Parameter> outputs,
                             String returnSort) {
    }

    /** The method's generic Signature attribute, or null when absent or malformed. */
    private static java.lang.classfile.MethodSignature methodSignatureOf(java.lang.classfile.MethodModel m) {
        return m.findAttribute(java.lang.classfile.Attributes.signature())
                .map(sa -> {
                    try {
                        return java.lang.classfile.MethodSignature
                                .parseFrom(sa.signature().stringValue());
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * The type parameters in scope of {@code m} -- its own and its class's --
     * whose bound carries {@code @Impure}, e.g. {@code <T extends @Impure Object>}.
     * The annotation sits on the bound, as a type-parameter-bound type
     * annotation on the method (method parameters) or the class (class
     * parameters). A static method does not see its class's parameters, and a
     * method parameter shadows a class parameter of the same name.
     */
    private static Set<String> impureBoundTypeParams(ClassModel cm, java.lang.classfile.MethodModel m,
                                                     java.lang.classfile.MethodSignature msig) {
        var out = new java.util.HashSet<String>();
        List<String> methodParams = msig == null ? List.of()
                : msig.typeParameters().stream().map(tp -> tp.identifier()).toList();
        if (!m.flags().has(AccessFlag.STATIC)) {
            List<String> classParams = Declarations.typeParamNamesOf(cm);
            addImpureBounds(org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(cm),
                    classParams, out);
            out.removeAll(methodParams);
        }
        addImpureBounds(org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(m),
                methodParams, out);
        return out;
    }

    private static void addImpureBounds(List<java.lang.classfile.TypeAnnotation> annotations,
                                        List<String> paramNames, Set<String> out) {
        for (var ta : annotations) {
            if (ta.targetPath().isEmpty()
                    && ta.targetInfo() instanceof java.lang.classfile.TypeAnnotation.TypeParameterBoundTarget b
                    && b.typeParameterIndex() < paramNames.size()
                    && org.strata.java.bytecode.frontend.ClassfileQueries.isImpure(ta.annotation())) {
                out.add(paramNames.get(b.typeParameterIndex()));
            }
        }
    }

    /**
     * Build a procedure's inputs (the {@code self} receiver of sort
     * {@code selfSort} for an instance method, then the parameters, named
     * from {@code shape}'s locals, or {@code v<slot>} when {@code shape} is
     * null for a method without code) and outputs (the result binding, unless
     * void), in signature-refusal order (params, return, throws). Side effects,
     * in order: each parameter name passes {@code refuseReservedName}, every class-typed
     * sort is registered as a referenced composite, and a class-typed return
     * sets the current method scope's {@code returnSort}.
     */
    private Signature buildSignature(String selfSort, java.lang.classfile.ClassModel cm,
                                     java.lang.classfile.MethodModel m,
                                     org.strata.java.bytecode.frontend.MethodBody shape,
                                     boolean isStatic, java.util.Set<String> typeParams,
                                     java.util.Set<String> methodTypeParams) {
        var ctx = translationContext();
        var mt = m.methodTypeSymbol();
        java.lang.classfile.MethodSignature msig = methodSignatureOf(m);
        // --- Inputs (signature-refusal order: params, return, throws) ---
        var inputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
        if (!isStatic) {
            composites.referencedCompositeTypes.add(selfSort);
            // A JArray member's receiver is the generic JArray<TArrayElement>.
            HighType selfType = ctx.method.arrayModelMember
                    ? composites.arrays.selfType()
                    : selfType(selfSort);
            inputs.add(new org.strata.jverify.laurel.Parameter(ident(LAUREL_SELF_BINDING), node(selfType)));
        }
        int paramCount = mt.parameterCount();
        boolean[] natP = new boolean[paramCount];
        boolean[] unbP = new boolean[paramCount];
        boolean natR = false;
        boolean unbR = false;
        for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(m)) {
            if (!ta.targetPath().isEmpty()) {
                continue;
            }
            String anno = java.lang.constant.ClassDesc
                    .ofDescriptor(ta.annotation().className().stringValue()).displayName();
            boolean isNat = anno.equals("Nat");
            boolean isUnb = anno.equals("Unbounded");
            if (!isNat && !isUnb) {
                continue;
            }
            if (ta.targetInfo().targetType()
                    == java.lang.classfile.TypeAnnotation.TargetType.METHOD_RETURN) {
                natR |= isNat;
                unbR |= isUnb;
            } else if (ta.targetInfo()
                    instanceof java.lang.classfile.TypeAnnotation.FormalParameterTarget fp
                    && fp.formalParameterIndex() < paramCount) {
                natP[fp.formalParameterIndex()] |= isNat;
                unbP[fp.formalParameterIndex()] |= isUnb;
            }
        }
        boolean sigArgs = msig != null && msig.arguments().size() == paramCount;
        boolean varargs = m.flags().has(java.lang.reflect.AccessFlag.VARARGS);
        boolean arraysEnabled = composites.arrays.enabled();
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < paramCount; i++) {
            String desc = mt.parameterType(i).descriptorString();
            var ps = shape != null ? shape.locals().at(slot, 0) : null;
            String pname = ps != null ? ps.name()
                    : shape != null ? shape.slotName().getOrDefault(slot, "v" + slot) : "v" + slot;
            LaurelFactory.refuseReservedName(pname);
            // ACC_VARARGS: the declaration reads `T...`, so the COMPONENT
            // type is what translates (the source `T`). A primitive
            // varargs parameter is an array the array model types exactly.
            boolean va = varargs && i == paramCount - 1 && desc.startsWith("[")
                    && !(arraysEnabled && ArrayModel.kindOfDescriptor(desc) != null);
            TypeRef t = null;
            if (sigArgs) {
                try {
                    String rendered = org.strata.java.bytecode.frontend.ClassfileQueries
                            .renderSig(msig.arguments().get(i));
                    if (va && rendered.endsWith("[]")) {
                        rendered = rendered.substring(0, rendered.length() - 2);
                    }
                    t = TypeRef.ofRendered(rendered, natP[i], unbP[i]);
                } catch (RuntimeException ignore) {
                    // fall back to the descriptor
                }
            }
            if (t == null) {
                t = TypeRef.ofDescriptor(va ? desc.substring(1) : desc, natP[i], unbP[i]);
            }
            if (arraysEnabled && t.kind() == TypeRef.Kind.ARRAY && nullableParameter(m, i)) {
                throw new JavaViolationException(ArrayModel.NULLABLE);
            }
            inputs.add(new org.strata.jverify.laurel.Parameter(ident(pname),
                    node(t.toLaurel(typeParams, methodTypeParams, resolver, composites))));
            slot += (desc.equals("J") || desc.equals("D")) ? 2 : 1;
        }

        // --- Outputs ---
        var outputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
        String returnSort = null;
        String retDesc = mt.returnType().descriptorString();
        if (!retDesc.equals("V")) {
            TypeRef rt = null;
            if (msig != null) {
                try {
                    rt = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries
                            .renderSig(msig.result()), natR, unbR);
                } catch (RuntimeException ignore) {
                    // fall back to the descriptor
                }
            }
            if (rt == null) {
                rt = TypeRef.ofDescriptor(retDesc, natR, unbR);
            }
            if (arraysEnabled && rt.kind() == TypeRef.Kind.ARRAY && nullableReturn(m)) {
                throw new JavaViolationException(ArrayModel.NULLABLE);
            }
            HighType resultType = rt.toLaurel(typeParams, methodTypeParams, resolver, composites);
            outputs.add(new org.strata.jverify.laurel.Parameter(LaurelFactory.reservedIdent(LAUREL_RESULT_BINDING),
                    ctx.contractMode ? node(resultType) : new AstNode<>(resultType, src.methodCrtRange(m))));
            if (rt.kind() == TypeRef.Kind.CLASS) {
                returnSort = resolver.qualifyTypeName(rt.nameWithScope(),
                        TypeRef.ofDescriptor(retDesc).fqnFromContext());
                ctx.method.returnSort = returnSort;
            }
        }
        return new Signature(inputs, outputs, returnSort);
    }

    /**
     * Translate one method into a Laurel procedure: signature (params,
     * return, throws join), contract header (preconditions, postconditions,
     * modifies, throwsOn cases), body via BodyTranslator/ControlFlowStructurer,
     * body-variant selection (Transparent/Opaque/Abstract), and the
     * verification-status record.
     *
     * @param member the library contract member this procedure translates,
     *        or null for a program method. A member is named by its Laurel
     *        name, gets its body from {@link #libraryMemberBody}, and records
     *        no verification status.
     */
    private void translateMethod(String classFqn, java.lang.classfile.ClassModel cm,
                                 java.lang.classfile.MethodModel m,
                                 MethodGraph.MethodInfo info,
                                 boolean verify, boolean isInterpretEntry) {
        translateMethod(classFqn, cm, m, info, verify, isInterpretEntry, null);
    }

    private void translateMethod(String classFqn, java.lang.classfile.ClassModel cm,
                                       java.lang.classfile.MethodModel m,
                                       MethodGraph.MethodInfo info,
                                       boolean verify, boolean isInterpretEntry,
                                       ContractMember member) {
        // The array model's debug-info scope is this method's (set once its
        // body is known) and ends with it.
        boolean outerScope = composites.arrays.inCodeWithoutDebugInfo;
        try {
            translateMethodInScope(classFqn, cm, m, info, verify, isInterpretEntry, member);
        } finally {
            composites.arrays.inCodeWithoutDebugInfo = outerScope;
        }
    }

    private void translateMethodInScope(String classFqn, java.lang.classfile.ClassModel cm,
                                        java.lang.classfile.MethodModel m,
                                        MethodGraph.MethodInfo info,
                                        boolean verify, boolean isInterpretEntry,
                                        ContractMember member) {
        boolean isStatic = m.flags().has(java.lang.reflect.AccessFlag.STATIC);
        String bareName = m.methodName().stringValue();
        // An overloaded name carries its erased parameter types, so same-named
        // siblings become distinct Laurel procedures instead of being refused.
        boolean isCtor = bareName.equals("<init>");
        String procName = member != null ? member.laurelName()
                : isCtor || translatingRelocatedContract
                        ? info.laurelName : bareName + resolver.overloadSuffix(classFqn, m);
        String methodName = isStatic ? classFqn + "_" + procName : procName;

        var mt = m.methodTypeSymbol();
        if (isInterpretEntry && mt.parameterCount() > 0) {
            throw new JavaViolationException(
                    "@InterpretEntry method '" + bareName + "' must take no arguments");
        }

        java.lang.classfile.MethodSignature msig = methodSignatureOf(m);
        var methodTypeParams = new java.util.LinkedHashSet<String>();
        if (msig != null) {
            msig.typeParameters().forEach(tp -> methodTypeParams.add(tp.identifier()));
        }
        var typeParams = new java.util.HashSet<String>(methodTypeParams);
        cm.findAttribute(java.lang.classfile.Attributes.signature()).ifPresent(sa -> {
            try {
                java.lang.classfile.ClassSignature.parseFrom(sa.signature().stringValue())
                        .typeParameters().forEach(tp -> typeParams.add(tp.identifier()));
            } catch (RuntimeException ignore) {
                // no class-level type parameters
            }
        });
        var shape = org.strata.java.bytecode.frontend.MethodBodyFactory.bodyOf(cm, m);
        if (shape == null) {
            throw new JavaViolationException("method has no code");
        }
        composites.arrays.inCodeWithoutDebugInfo = shape.hasLocalsWithoutDebugInfo();
        // A JArray member also types its class's type parameter as a TVar:
        // the JArray composite declares it, so the procedure does not.
        boolean arrayModelMember = member != null && member.kind() == ContractMember.Kind.ARRAY_MODEL;
        var tvarNames = new java.util.LinkedHashSet<String>(methodTypeParams);
        if (arrayModelMember) {
            tvarNames.addAll(Declarations.typeParamNamesOf(cm));
        }
        var ctx = translationContext();
        ctx.currentClassFqn = classFqn;
        ctx.typeParams = typeParams;
        ctx.methodTypeParams = tvarNames;
        ctx.impureTypeParams = impureBoundTypeParams(cm, m, msig);
        ctx.beginMethod(info);
        ctx.method.arrayModelMember = arrayModelMember;
        ctx.method.pure = member == null && hasAnnotation(m, "Transparent");
        ctx.method.relocatedContract = translatingRelocatedContract;

        // Input compiled without -Xjcov: no statement spans exist, so anchor
        // every emitted range at the method's LineNumberTable line envelope.
        // Diagnostics then carry method (line) precision and per-method
        // accounting still attributes.
        FileRange methodAnchor = UNKNOWN_RANGE;
        if (shape.crt().isEmpty()) {
            if (shape.lines() != null) {
                methodAnchor = src.lineSpanRange(shape.lines().minLine(), shape.lines().maxLine());
            }
            src.defaultRange = methodAnchor;
        }

        if (member == null) {
            // Floating-point modulo, reported BEFORE the signature is translated.
            // Laurel has no float/double at all, so a method mentioning one is refused
            // on its parameter or return type first, and the more specific `%`
            // diagnostic the fixtures pin would never be reached.
            reportFloatingPointModulo(shape, methodAnchor, info);
            if (new ContractPlacementChecker(reporter, src).checkIsAbstractPlacement(m, info.quiet)) {
                info.constructErrorReported = true;
            }
            if (translatingRelocatedContract && !hasAnnotation(m, "Transparent") && contractBodyHasStatements(m)) {
                reporter.reportError(src.uri, src.pointOf(src.methodCrtRange(m)),
                        "a contract for an impure method may only call super or this, or throw a ContractException in its body");
                info.constructErrorReported = true;
            }
        }
        // --- Inputs and outputs ---
        var signature = buildSignature(classFqn, cm, m, shape, isStatic, typeParams, tvarNames);
        var inputs = signature.inputs();
        var outputs = signature.outputs();

        // --- Throws clause (Exceptions attribute; the generic signature
        // preserves a type-variable clause, which refuses) ---
        Optional<AstNode<HighType>> throwsType = Optional.empty();
        Optional<Identifier> throwsBinding = Optional.empty();
        var exAttr = m.findAttribute(java.lang.classfile.Attributes.exceptions()).orElse(null);
        if (exAttr != null && !exAttr.exceptions().isEmpty()) {
            List<String> thrown = new ArrayList<>();
            if (msig != null && !msig.throwableSignatures().isEmpty()) {
                for (var ts : msig.throwableSignatures()) {
                    String rendered;
                    try {
                        rendered = org.strata.java.bytecode.frontend.ClassfileQueries
                                .renderSig((java.lang.classfile.Signature) ts);
                    } catch (RuntimeException e) {
                        rendered = null;
                    }
                    TypeRef t = rendered != null ? TypeRef.ofRendered(rendered, false, false) : null;
                    if (t == null || t.kind() != TypeRef.Kind.CLASS
                            || (!t.scoped() && methodTypeParams.contains(t.nameWithScope()))) {
                        throw new JavaViolationException(
                                "Unsupported thrown type in throws clause: "
                                        + (rendered != null ? rendered : ts));
                    }
                    thrown.add(resolveClassFqn(t.nameWithScope()));
                }
            } else {
                for (var ce : exAttr.exceptions()) {
                    thrown.add(JavaNames.dottedFqn(ce.asInternalName()));
                }
            }
            String join = thrown.get(0);
            for (var cls : thrown.subList(1, thrown.size())) {
                join = leastCommonAncestor(cls, join);
            }
            for (var cls : thrown) {
                registerHierarchyUpToThrowable(cls);
            }
            composites.referencedCompositeTypes.add(join);
            ctx.method.throwsJoin = join;
            throwsType = Optional.of(node(new HighType.UserDefined(ident(join))));
            throwsBinding = Optional.of(ident(THROWN_BINDING));
        }

        // --- Throw-coverage prescan, then the body run ---
        if (!ctx.method.relocatedContract) {
            ThrowCoveragePrescan.check(shape, ctx);
            if (member == null) {
                ThrowCoveragePrescan.checkDeadHandlers(shape, ctx);
            }
        }

        bodyMethod = m;
        BodyRun run;
        try {
            run = runBody(cm, shape, ctx);
        } finally {
            bodyMethod = null;
        }
        if (!run.body().todos.isEmpty()) {
            throw new JavaViolationException(
                    "unsupported control flow (decompiler could not structure: "
                            + run.body().todos.get(0).strip() + ")");
        }
        if (run.refusal() != null) {
            // Contract-header conversion refusals fire before body-statement
            // refusals (an ordering pinned by the corpus).
            contractRefusalPass(run.body(), ctx);
            throw run.refusal();
        }
        var topStmts = run.topStmts();
        var body = run.body();

        // --- Contract header scan (over the spec markers) ---
        var pres = new ArrayList<AstNode<StmtExpr>>();
        var posts = new ArrayList<AstNode<StmtExpr>>();
        var invs = new ArrayList<AstNode<StmtExpr>>();
        var mods = new ArrayList<AstNode<StmtExpr>>();
        var throwsOns = new ArrayList<AstNode<StmtExpr>>();
        int hdr = 0;
        var ctorRest = new ArrayList<AstNode<StmtExpr>>();
        for (; hdr < topStmts.size(); hdr++) {
            var spec = body.specStmts.get(topStmts.get(hdr));
            if (spec == null && isCtor) {
                ctorRest.add(topStmts.get(hdr));
                continue;
            }
            if (spec == null) {
                break;
            }
            switch (spec.name()) {
                case "precondition" -> pres.add(topStmts.get(hdr));
                case "postcondition" -> posts.add(topStmts.get(hdr));
                case "invariant" -> invs.add(topStmts.get(hdr));
                case "decreases", "reads" -> {
                    // recognized so they don't break the header scan; dropped
                    // at Laurel emission
                }
                case "modifies" -> mods.add(topStmts.get(hdr));
                case "throwsOn" -> throwsOns.add(topStmts.get(hdr));
                default -> {
                }
            }
        }
        if (!invs.isEmpty()) {
            throw new JavaViolationException("invariants are not allowed in a method");
        }

        // --- Library member: does the contract carry an implementation? ---
        // None when the body after the header is empty (spec only) or is
        // exactly one `throw new ContractException()`.
        boolean noImplementation = false;
        if (member != null) {
            var nonHoisted = topStmts.subList(hdr, topStmts.size());
            boolean contractThrowBody = nonHoisted.size() == 1
                    && body.contractThrows.contains(nonHoisted.get(0));
            noImplementation = nonHoisted.isEmpty() || contractThrowBody;
            if (body.contractThrows.size() > (contractThrowBody ? 1 : 0)) {
                throw new JavaViolationException(
                        "ContractException may only be thrown as the whole body of a contract method");
            }
            if (!noImplementation && !member.isTransparent()) {
                throw new JavaViolationException("a contract for an impure method may only call super"
                        + " or this, or throw a ContractException in its body");
            }
        }

        var preconditions = new ArrayList<Condition>();
        boolean abstractPrecondition = false;
        for (var stmt : pres) {
            var spec = body.specStmts.get(stmt);
            if (spec.args().size() != 1) {
                throw new JavaViolationException(
                        "A call to 'precondition' may have only one argument");
            }
            if (spec.args().get(0).val() instanceof StmtExpr.LiteralString ls
                    && ls.value().equals(SpecCallTranslator.ABSTRACT_MARKER)) {
                abstractPrecondition = true;
                continue;
            }
            FileRange condRange = stmt.source();   // statement-level; no sub-range from bytecode
            boolean lambda = spec.lambdaArg() != null;
            src.defaultRange = condRange;
            AstNode<StmtExpr> converted;
            if (lambda) {
                converted = body.translateLambdaBody(spec.lambdaArg(), Map.of());
            } else {
                converted = spec.args().get(0);
                body.checkContractArg(converted);
            }
            BodyTranslator.refuseOld(converted, "a precondition");
            converted = body.coerce(ArrayAccessTranslator.stripLoadFacts(converted), "Z");
            preconditions.add(new Condition(
                    LaurelTrees.rerange(converted, condRange, ctx.method.statementNodes),
                    Optional.empty(), new ConditionMode.Both()));
        }

        boolean baseAbstract = !isStatic && !isCtor && overridesAbstractPrecondition(classFqn, m);
        if (!isStatic && !isCtor && (abstractPrecondition || baseAbstract)) {
            String fnName = "thePreconditionOf$" + bareName;
            Body fnBody;
            if (abstractPrecondition) {
                fnBody = new Body.Opaque(List.of(), Optional.empty(), List.of());
            } else {
                AstNode<StmtExpr> conj = null;
                for (var c : preconditions) {
                    conj = conj == null ? c.condition()
                            : noRange(primitiveOp(new Operation.AndThen(), conj, c.condition()));
                }
                fnBody = new Body.Transparent(noRange(new StmtExpr.Block(List.of(noRange(new StmtExpr.Return(
                        List.of(conj == null ? noRange(new StmtExpr.LiteralBool(true)) : conj)))),
                        Optional.empty())));
            }
            var fn = new Procedure(ident(fnName), List.of(), inputs,
                    List.of(new org.strata.jverify.laurel.Parameter(LaurelFactory.reservedIdent(LAUREL_RESULT_BINDING),
                            node(new HighType.TBool()))),
                    List.of(), new CoroutineContracts.Regular(), Optional.empty(), fnBody, Optional.empty(), false,
                    List.of(), Optional.empty(), Optional.empty(), List.of(),
                    /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
            if (!(abstractPrecondition && baseAbstract)) {
                composites.instanceProcsBySort.computeIfAbsent(classFqn, k -> new ArrayList<>()).add(fn);
            }
            if (abstractPrecondition || !preconditions.isEmpty()) {
                var args = new ArrayList<AstNode<StmtExpr>>();
                for (int i = 1; i < inputs.size(); i++) {
                    args.add(noRange(new StmtExpr.Var(new Variable.Local(inputs.get(i).name()))));
                }
                FileRange preRange = pres.isEmpty() ? UNKNOWN_RANGE : pres.get(0).source();
                preconditions.clear();
                preconditions.add(new Condition(node(new StmtExpr.InstanceCall(
                        noRange(new StmtExpr.Var(new Variable.Local(inputs.get(0).name()))), ident(fnName), args),
                        preRange), Optional.empty(), new ConditionMode.Both()));
            }
        }

        var postconditions = new ArrayList<Condition>();
        for (var stmt : posts) {
            var spec = body.specStmts.get(stmt);
            if (spec.args().size() != 1) {
                throw new JavaViolationException(
                        "A call to 'postcondition' may have only one argument");
            }
            FileRange condRange = stmt.source();   // statement-level; no sub-range from bytecode
            boolean lambda = spec.lambdaArg() != null;
            src.defaultRange = condRange;
            AstNode<StmtExpr> converted;
            if (lambda) {
                var binders = body.lambdaBinders(spec.lambdaArg());
                Map<String, String> ren = binders != null && binders.size() == 1
                        ? Map.of(binders.get(0).name(), LAUREL_RESULT_BINDING)
                        : spec.lambdaArg().isMethodRef()
                                ? Map.of(LAUREL_RESULT_BINDING, LAUREL_RESULT_BINDING)
                                : Map.of();
                converted = body.translateLambdaBody(spec.lambdaArg(), ren);
            } else {
                converted = spec.args().get(0);
                body.checkContractArg(converted);
            }
            converted = body.coerce(ArrayAccessTranslator.stripLoadFacts(converted), "Z");
            postconditions.add(new Condition(
                    LaurelTrees.rerange(converted, condRange, ctx.method.statementNodes),
                    Optional.empty(), new ConditionMode.Both()));
        }

        var explicitModifies = new ArrayList<AstNode<StmtExpr>>();
        // A user-written `modifies` on a @Transparent method is an error. Not inert
        // otherwise: a @Transparent method is meant to be a
        // transparent body, which cannot carry a frame, so the clause would silently
        // demote it to an opaque procedure -- a different translation instead of a
        // diagnostic. The JVerify.modifies call survives into bytecode, so this needs
        // no source structure. Only the USER's clause: a frame the front end
        // synthesizes for a heap-writing body is legitimate.
        boolean pureMethod = hasAnnotation(m, "Transparent");
        if (member == null && verify && pureMethod) {
            for (var stmt : mods) {
                reporter.reportError(src.uri, positionRange(stmt.source()),
                        "modifies clauses are not allowed in a transparent method");
                info.constructErrorReported = true;
            }
        }
        // Likewise a postcondition: a transparent body has no slot for one. Contract
        // sources too: a relocated source contract and a library contract member.
        if (pureMethod && (verify || translatingRelocatedContract)) {
            for (var stmt : posts) {
                reporter.reportError(src.uri, positionRange(stmt.source()),
                        "postconditions are not allowed in a transparent method");
                info.constructErrorReported = true;
            }
        }
        for (var stmt : mods) {
            var spec = body.specStmts.get(stmt);
            if (spec.args().size() != 1) {
                throw new JavaViolationException(
                        "A call to 'modifies' may have only one argument");
            }
            FileRange argRange = stmt.source();
            src.defaultRange = argRange;
            var arg = spec.args().get(0);
            var argSpec = body.specStmts.get(arg);
            if (argSpec != null && argSpec.name().equals("everything")) {
                explicitModifies.add(node(new StmtExpr.All(), argRange));
            } else if (arrayModelMember) {
                // A JArray member may change only its own contents.
                if (!(arg.val() instanceof StmtExpr.Var selfVar
                        && selfVar.var_() instanceof Variable.Local selfLocal
                        && selfLocal.name().text().equals(LAUREL_SELF_BINDING))) {
                    throw new JavaViolationException(ArrayModel.UNAVAILABLE);
                }
                explicitModifies.add(ArrayModel.frameTarget(
                        LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes)));
            } else {
                body.checkContractArg(arg);
                explicitModifies.add(modifiesTarget(arg,
                        LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes), body));
            }
        }

        if (!throwsOns.isEmpty() && ctx.method.throwsJoin == null) {
            throw new JavaViolationException(
                    "throwsOn requires the method to declare the thrown type in its throws clause");
        }
        var throwsOnBlocks = new ArrayList<org.strata.jverify.laurel.ThrowsOnBlock>();
        for (var stmt : throwsOns) {
            var spec = body.specStmts.get(stmt);
            FileRange stmtRange = stmt.source();
            FileRange guardRange = stmtRange;
            src.defaultRange = guardRange;
            var guardArg = spec.args().get(0);
            body.checkPlaceable(guardArg);
            BodyTranslator.refuseOld(guardArg, "a throwsOn guard");
            var guard = LaurelTrees.rerange(body.coerce(guardArg, "Z"), guardRange,
                    ctx.method.statementNodes);
            List<Condition> casePosts = new ArrayList<>();
            List<AstNode<StmtExpr>> caseModifies = new ArrayList<>();
            src.defaultRange = stmtRange;
            for (var link : spec.links()) {
                switch (link.name()) {
                    case "postcondition" -> casePosts.add(
                            convertCasePostcondition(link, body, stmtRange, ctx));
                    case "modifies" -> {
                        var arg = link.arg();
                        var argSpec = body.specStmts.get(arg);
                        if (argSpec != null && argSpec.name().equals("everything")) {
                            caseModifies.add(node(new StmtExpr.All(), stmtRange));
                        } else {
                            body.checkPlaceable(arg);
                            caseModifies.add(modifiesTarget(arg, LaurelTrees.rerange(arg, stmtRange,
                                    ctx.method.statementNodes), body));
                        }
                    }
                    default -> throw new IllegalStateException(link.name());
                }
            }
            throwsOnBlocks.add(new org.strata.jverify.laurel.ThrowsOnBlock(
                    guard, casePosts, caseModifies));
        }

        // --- Implementation: hoisted declarations, then the structured rest ---
        var implementation = new ArrayList<AstNode<StmtExpr>>(hoistedDeclStmts(shape, body, ctx));
        for (var stmt : isCtor ? ctorRest : topStmts.subList(hdr, topStmts.size())) {
            checkNoSpecMarkers(stmt, body);
            implementation.add(stmt);
        }
        // Every offending construct has now been reported at its own position, so
        // demote without a second, vaguer method-level diagnostic. Deliberately
        // after the whole body: reporting and then throwing at the first one would
        // leave the rest of the method's errors unmentioned, which is exactly what
        // the per-method refusal did.
        if (info.constructErrorReported) {
            throw new ReportedViolation();
        }
        StmtExpr methodBody = new StmtExpr.Block(implementation, Optional.empty());

        // --- Body-variant selection ---
        boolean isTransparent = hasAnnotation(m, "Transparent");
        List<AstNode<StmtExpr>> modifies = !explicitModifies.isEmpty()
                ? explicitModifies
                : isCtor && onlySelfWrites(implementation, body)
                        ? List.of(noRange(new StmtExpr.Var(new Variable.Local(inputs.get(0).name()))))
                        : writesHeap(implementation, body)
                                ? List.of(noRange(new StmtExpr.All()))
                                : List.of();
        if (isCtor) {
            postconditions.addAll(constructorFieldFacts(implementation, inputs));
            for (int i = 1; i < inputs.size(); i++) {
                if (inputs.get(0).type().val() instanceof HighType.UserDefined
                        && inputs.get(i).type().val() instanceof HighType.UserDefined u
                        && composites.referencedCompositeTypes.contains(u.name().text())) {
                    preconditions.add(new Condition(noRange(primitiveOp(new Operation.Not(),
                            noRange(new StmtExpr.ReferenceEquals(
                                    noRange(new StmtExpr.Var(new Variable.Local(inputs.get(0).name()))),
                                    noRange(new StmtExpr.Var(new Variable.Local(inputs.get(i).name()))))))),
                            Optional.empty(), new ConditionMode.Assume()));
                }
            }
        }
        Body procBody;
        if (member != null) {
            procBody = libraryMemberBody(member, noImplementation, postconditions, explicitModifies,
                    implementation, methodBody);
            // Strata needs a termination measure for a recursive function,
            // and `decreases` clauses are not emitted.
            if (procBody instanceof Body.Transparent && info.callees.contains(member.calleeKey())) {
                throw new JavaViolationException("a recursive contract function is not yet supported:"
                        + " its 'decreases' clause is not emitted");
            }
            // Strata checks a transparent body's range-constrained result in a
            // generated $constraintLemma that does not assume its callees'
            // postconditions, so even a correct body (charAt's chars.get(index))
            // fails there and taints every caller's verdict.
            if (procBody instanceof Body.Transparent && hasConstrainedOutput(outputs)) {
                throw new JavaViolationException("a contract body that returns '"
                        + mt.returnType().displayName()
                        + "' is not yet supported: Strata cannot check the result's range");
            }
            refuseUnfixedTypeParams(methodTypeParams, inputs);
        } else if (ctx.method.relocatedContract) {
            // A relocated contract method is a contract with no implementation:
            // `opaque` with its clauses as ensures. Not the
            // skipped-method arms below -- a contract's postconditions are the point,
            // so they are kept even for a @Transparent one, and callers are not refused.
            boolean returns = implementation.stream().anyMatch(st -> st.val() instanceof StmtExpr.Return);
            boolean libraryContract = resolver.libraryContracts.values().stream().anyMatch(c -> c.decl().cm() == cm);
            procBody = isTransparent && postconditions.isEmpty() && returns && libraryContract
                    ? new Body.Transparent(noRange(methodBody))
                    : new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(
                            !libraryContract && explicitModifies.isEmpty()
                                    && m.methodTypeSymbol().returnType().descriptorString().equals("V")
                                    ? List.of(noRange(new StmtExpr.All()))
                                    : modifies));
        } else if (!verify) {
            // A skipped method's postconditions were never checked, so a caller
            // must not assume them. A PURE one is emitted as a DECLARATION only --
            // signature and preconditions, no implementation, no postconditions,
            // empty frame -- which is what makes a `@Verify(false) @Transparent` callee
            // callable-but-invisible (see PureUnverifiedMethod) rather than
            // cascade-demoting its callers. A non-pure one keeps its declared
            // postconditions only because every call to it is still refused at
            // the call site: there is no `modifies *` in the Laurel bindings, so
            // it cannot be given a frame that is honest about what it may write.
            //
            // The declaration's EMPTY FRAME is a positive promise that the call
            // changes nothing, and purity does not imply it: a @Transparent method may write
            // the heap, and then carries a synthesized `modifies *` (or a user's
            // clause). So a pure method keeps the declaration only when its frame is
            // already empty and it has no throwsOn block (whose exceptional
            // postconditions a declaration would pass through unchecked). Otherwise
            // it is dropped and its callers cascade-demote, as for a non-pure callee;
            // without this a caller would PROVE `b.f == 0` across a call that may
            // write `b.f`
            // (PureSkippedWithFrameIsDropped, PureMethodWithModifies).
            if (isTransparent && !(modifies.isEmpty() && throwsOnBlocks.isEmpty())) {
                info.refused = true;
                info.cascadeEvenIfQuiet = true;
            }
            procBody = isTransparent || postconditions.isEmpty()
                    ? new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()))
                    : new Body.Abstract(postconditions);
        } else if (isTransparent) {
            // Whatever the body contains: what a transparent body may not do (loop,
            // write the heap, throw) is Strata's to report. Postconditions and a
            // user `modifies` were reported above. The body becomes a function,
            // where Strata cannot link the opaque array load helper: element reads
            // use the transparent one.
            procBody = new Body.Transparent(ArrayAccessTranslator.pureBodyLoads(noRange(methodBody), lf));
        } else {
            procBody = new Body.Opaque(postconditions, Optional.of(noRange(methodBody)),
                    unguardedModifies(modifies));
        }
        if (!verify) {
            // Only the conditions of an opted-out method are emitted, so only
            // their calls reference other procedures (the reachability roots).
            for (var c : preconditions) {
                collectCalleeNames(c.condition(), info.specCalleeNames);
            }
            for (var c : postconditions) {
                collectCalleeNames(c.condition(), info.specCalleeNames);
            }
            for (var block : throwsOnBlocks) {
                collectCalleeNames(block.guard(), info.specCalleeNames);
                for (var c : block.postconditions()) {
                    collectCalleeNames(c.condition(), info.specCalleeNames);
                }
                for (var target : block.modifies()) {
                    collectCalleeNames(target, info.specCalleeNames);
                }
            }
        }
        var proc = new Procedure(
                // The name carries the method's own range. Strata anchors an
                // override-refinement (Liskov) failure at the OVERRIDE's name and
                // only falls back to the inherited clause when that name has no
                // position, so an unpositioned name moved the diagnostic onto the
                // base's `ensures` line (PolyDispatchSoundness,
                // OverrideWithSubclassOverload).
                member != null ? ident(methodName) : withRange(ident(methodName), src.methodCrtRange(m)),
                methodTypeParams.stream().map(LaurelFactory::ident).toList(),
                inputs, outputs, preconditions, new CoroutineContracts.Regular(),
                Optional.empty(), procBody, Optional.empty(),
                isInterpretEntry, List.of(),
                throwsType, throwsBinding, throwsOnBlocks,
                /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
        if (isStatic) {
            procedures.add(proc);
        } else {
            composites.instanceProcsBySort.computeIfAbsent(classFqn, k -> new ArrayList<>()).add(proc);
        }
        if (member != null) {
            // Library members are never counted: no verification status.
            info.proc = proc;
            info.sort = isStatic ? null : classFqn;
            return;
        }
        var range = src.methodCrtRange(m);
        if (range == UNKNOWN_RANGE) {
            range = src.methodRangeOf(shape.crt());
        }
        if (range == UNKNOWN_RANGE && shape.lines() != null) {
            // No CRT: the LineNumberTable's line envelope stands in, so
            // line-stamped diagnostics still attribute to this method.
            range = src.lineSpanRange(shape.lines().minLine(), shape.lines().maxLine());
        }
        if (range == UNKNOWN_RANGE) {
            range = methodAnchor;   // no LNT either: the lexical name anchor
        }
        var status = new MethodVerificationStatus(bareName,
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                verify || (ctx.method.relocatedContract && hasAnnotation(m, "Transparent")
                        && implementation.stream().anyMatch(st -> st.val() instanceof StmtExpr.Return))
                        ? MethodVerificationStatus.VerificationStatus.Verified
                        : MethodVerificationStatus.VerificationStatus.Skipped);
        recordStatus(range, status);
        info.status = status;
        info.proc = proc;
        info.sort = isStatic ? null : classFqn;
    }

    // =====================================================================
    // The compilation walk over the declaration tree ({@link Declarations})
    // and the per-class/per-method @Verify policy.
    // =====================================================================

    /** The verify mode of an element, from its RuntimeInvisibleAnnotations. */
    private static ShouldVerifyMode verifyModeOf(java.lang.classfile.AttributedElement el) {
        var verify = ClassResolver.verifyAnnotation(el);
        if (verify == null) {
            return ShouldVerifyMode.Inherit;
        }
        boolean should = ClassResolver.verifyValue(verify, "value", true);
        boolean overrideChildren = ClassResolver.verifyValue(verify, "overrideChildren", false);
        if (should) {
            return overrideChildren ? ShouldVerifyMode.AlwaysYes : ShouldVerifyMode.DefaultYes;
        }
        return overrideChildren ? ShouldVerifyMode.AlwaysNo : ShouldVerifyMode.DefaultNo;
    }

    private final Map<String, java.lang.classfile.ClassModel> packageInfos = new HashMap<>();

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private void compileUnit(ClassSource cls, Declarations.TypeNode root) {
        // The direct path resolves class references from descriptors and the
        // classfile pool; no import table exists or is needed.
        resolver.imports = Map.of();
        var packageInfo = packageInfos.get(packageOf(cls.internalName()));
        if (packageInfo != null) {
            pushShouldVerify(verifyModeOf(packageInfo));
        }
        try {
            compileType(root);
            registerUntranslatedMethods(cls);
        } finally {
            if (packageInfo != null) {
                shouldVerifies.pop();
            }
        }
    }

    private void warnMissingSuperInitContract(Declarations.TypeNode node) {
        String superFqn = resolver.programSuper.get(node.fqn());
        if (superFqn == null || decls.programTypes.containsKey(superFqn)
                || !isThrowableSubtype(superFqn)) {
            return;
        }
        // Anchor at this class's declaration line (the earliest member line,
        // per MethodBodyFactory.classDeclLine); the corpus pins this anchor.
        reporter.reportWarning(src.uri,
                src.linePoint(org.strata.java.bytecode.frontend.MethodBodyFactory.classDeclLine(node.cm())),
                "missing contract for method '<init>' in class '" + superFqn + "'");
    }

    private void compileType(Declarations.TypeNode node) {
        if (decls.contractClasses.contains(node.fqn())) {
            return; // consumed as a contract definition, not user code
        }
        warnMissingSuperInitContract(node);
        pushShouldVerify(verifyModeOf(node.cm()));
        try {
            var methods = Declarations.declaredMethodsOf(node.cm());
            for (var m : methods) {
                compileMethod(node, m, Declarations.ordinalOf(methods, m));
            }
            // Where a constructor's contract calls sit is checked even when the
            // constructor is not translated: a misplaced clause would otherwise be
            // silently ignored.
            var placement = new ContractPlacementChecker(reporter, src);
            // Initializer blocks are class members, so the CLASS's opt-out governs
            // them (ClassLevelOptOutSuppressesConstructs).
            if (shouldVerify()) {
                placement.checkInitializerBlocks(node.cm());
            }
            for (var m : node.cm().methods()) {
                if (!m.methodName().stringValue().equals("<init>")
                        || (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_SYNTHETIC) != 0) {
                    continue;
                }
                pushShouldVerify(verifyModeOf(m));
                boolean verifyCtor = shouldVerify();
                shouldVerifies.pop();
                if (verifyCtor) {
                    placement.checkConstructorPlacement(node.cm(), m);
                }
                if (verifyCtor && Constructors.eligible(node.cm(), m)) {
                    compileConstructor(node, m);
                }
            }
            for (var nested : node.nested()) {
                compileType(nested);
            }
        } finally {
            shouldVerifies.pop();
        }
    }

    private final java.util.Set<String> attemptedConstructors = new java.util.HashSet<>();

    private void compileConstructor(Declarations.TypeNode node, java.lang.classfile.MethodModel m) {
        String fqn = node.fqn();
        String laurelName = Constructors.procName(fqn, m.methodTypeSymbol());
        FileRange mRange = src.methodCrtRange(m);
        attemptedConstructors.add(laurelName);
        var info = new MethodGraph.MethodInfo(fqn, "<init>", laurelName, false,
                src.uri, src.pointOf(mRange), attemptedConstructors.size(), false);
        graph.methodInfos.put(info.key(), info);
        try {
            translateMethod(fqn, node.cm(), m, info, true, false);
        } catch (ReportedViolation e) {
            info.refused = true;
            recordRefusedStatus(info, "<init>", mRange);
        } catch (JavaViolationException e) {
            info.refused = true;
            reportRefusal(info, e.getMessage());
            recordRefusedStatus(info, "<init>", mRange);
        }
    }

    private void stripCallsToUntranslatedConstructors() {
        var live = new java.util.HashSet<String>();
        for (var info : graph.methodInfos.values()) {
            if (info.name.equals("<init>") && info.proc != null && !info.refused) {
                live.add(info.laurelName);
            }
        }
        var calls = translationContext().constructorCalls;
        java.util.function.UnaryOperator<AstNode<StmtExpr>> f = n ->
                n.val() instanceof StmtExpr.InstanceCall c && calls.contains(c.callee().text())
                        && !live.contains(c.callee().text())
                        ? node(new StmtExpr.Block(List.of(), Optional.empty()), n.source())
                        : n;
        var replaced = new java.util.IdentityHashMap<Procedure, Procedure>();
        for (var info : graph.methodInfos.values()) {
            if (info.proc == null) {
                continue;
            }
            Procedure p = info.proc;
            Body body = switch (p.body()) {
                case Body.Opaque o -> new Body.Opaque(o.postconditions(),
                        o.implementation().map(i -> LaurelTrees.map(i, f)), o.modifies());
                case Body.Transparent t -> new Body.Transparent(LaurelTrees.map(t.body(), f));
                default -> p.body();
            };
            Procedure q = new Procedure(p.name(), p.typeArgs(), p.inputs(), p.outputs(), p.preconditions(),
                    p.contracts(), p.decreases(), body, p.invokeOn(), p.isInterpretEntry(), p.axioms(),
                    p.throwsType(), p.throwsBinding(), p.throwsOn(), p.readsGlobals(), p.writesGlobals());
            replaced.put(p, q);
            info.proc = q;
        }
        procedures.replaceAll(p -> replaced.getOrDefault(p, p));
        composites.instanceProcsBySort.replaceAll((sort, procs) ->
                new ArrayList<>(procs.stream().map(p -> replaced.getOrDefault(p, p)).toList()));
    }

    private void compileMethod(Declarations.TypeNode node, java.lang.classfile.MethodModel m, int ordinal) {
        pushShouldVerify(verifyModeOf(m));
        boolean verify = shouldVerify();
        shouldVerifies.pop();
        var verifiedOrSkipped = verify
                ? MethodVerificationStatus.VerificationStatus.Verified
                : MethodVerificationStatus.VerificationStatus.Skipped;
        String name = m.methodName().stringValue();
        String fqn = node.fqn();
        FileRange mRange = src.methodCrtRange(m);

        boolean isInterpretEntry = hasAnnotation(m, "InterpretEntry");
        boolean isStatic = m.flags().has(java.lang.reflect.AccessFlag.STATIC);
        if (!isStatic && isInterpretEntry) {
            reporter.reportError(src.uri, src.pointOf(mRange),
                    "@InterpretEntry method '" + name + "' must be static");
            recordStatus(mRange, new MethodVerificationStatus(name,
                    mRange == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(mRange),
                    MethodVerificationStatus.VerificationStatus.Skipped));
            return;
        }
        if (m.code().isEmpty() && relocateFromContract(node, m, fqn, name, isStatic, mRange, ordinal)) {
            return;
        }
        if (m.code().isEmpty()) {
            if (hasAnnotation(m, "EmptyContract")) {
                recordStatus(mRange, new MethodVerificationStatus(name,
                        mRange == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(mRange),
                        verifiedOrSkipped));
                probeBodylessMethod(node, m, verify);
                return;
            }
            probeBodylessMethod(node, m, verify);
            return;
        }
        var info = new MethodGraph.MethodInfo(fqn, name,
                name + resolver.overloadSuffix(fqn, m), isStatic,
                src.uri, src.pointOf(mRange), ordinal, !verify);
        graph.methodInfos.put(info.key(), info);
        try {
            translateMethod(fqn, node.cm(), m, info, verify, isInterpretEntry, null);
        } catch (ReportedViolation e) {
            info.refused = true;
            recordRefusedStatus(info, name, mRange);
        } catch (JavaViolationException e) {
            info.refused = true;
            reportRefusal(info, e.getMessage());
            recordRefusedStatus(info, name, mRange);
        }
    }

    /**
     * Replace every {@code preconditionOf} placeholder with its callee's preconditions,
     * the callee's parameters substituted by the call's arguments (and its receiver for
     * {@code self}), conjoined -- {@code true} when there are none. It has to wait
     * until the callee is translated.
     *
     * <p>A callee with no procedure (it was refused) has no preconditions to inline.
     * {@code true} would be sound only in a precondition, so the enclosing method is
     * demoted instead, and the cascade demotes its callers.
     */
    private void inlinePreconditionOf() {
        var ctx = translationContext();
        if (ctx.pendingPreconditionOf.isEmpty()) {
            return;
        }
        var replaced = new java.util.IdentityHashMap<Procedure, Procedure>();
        for (var info : graph.methodInfos.values()) {
            if (info.proc == null) {
                continue;
            }
            boolean[] missing = {false};
            Procedure p = info.proc;
            java.util.function.UnaryOperator<AstNode<StmtExpr>> f = n -> {
                if (!(n.val() instanceof StmtExpr.StaticCall c)
                        || !ctx.pendingPreconditionOf.containsKey(c.callee().text())) {
                    return n;
                }
                var pending = ctx.pendingPreconditionOf.get(c.callee().text());
                Procedure callee = calleeProcedure(pending);
                if (callee == null) {
                    missing[0] = true;
                    return node(new StmtExpr.LiteralBool(true), n.source());
                }
                return node(inlinedPreconditions(callee, pending), n.source());
            };
            var pre = p.preconditions().stream()
                    .map(c -> new Condition(LaurelTrees.map(c.condition(), f), c.summary(), c.mode()))
                    .toList();
            Body body = switch (p.body()) {
                case Body.Opaque o -> new Body.Opaque(o.postconditions().stream()
                        .map(c -> new Condition(LaurelTrees.map(c.condition(), f), c.summary(), c.mode()))
                        .toList(), o.implementation().map(i -> LaurelTrees.map(i, f)), o.modifies());
                case Body.Transparent t -> new Body.Transparent(LaurelTrees.map(t.body(), f));
                case Body.Abstract a -> new Body.Abstract(a.postconditions().stream()
                        .map(c -> new Condition(LaurelTrees.map(c.condition(), f), c.summary(), c.mode()))
                        .toList());
                default -> p.body();
            };
            Procedure q = new Procedure(p.name(), p.typeArgs(), p.inputs(), p.outputs(), pre,
                    p.contracts(), p.decreases(), body, p.invokeOn(), p.isInterpretEntry(), p.axioms(),
                    p.throwsType(), p.throwsBinding(), p.throwsOn(), p.readsGlobals(), p.writesGlobals());
            replaced.put(p, q);
            info.proc = q;
            if (missing[0]) {
                info.refused = true;
                reportRefusal(info, "preconditionOf names a method that could not be translated");
            }
        }
        procedures.replaceAll(p -> replaced.getOrDefault(p, p));
        composites.instanceProcsBySort.replaceAll((sort, procs) ->
                new ArrayList<>(procs.stream().map(p -> replaced.getOrDefault(p, p)).toList()));
    }

    /** The emitted procedure of a pending {@code preconditionOf}'s callee, or null. */
    private Procedure calleeProcedure(TranslationContext.PendingPreconditionOf pending) {
        String name = pending.callee().methodName().stringValue();
        String laurelName = name + resolver.overloadSuffix(pending.calleeFqn(), pending.callee());
        for (var info : graph.methodInfos.values()) {
            if (info.classFqn.equals(pending.calleeFqn()) && info.laurelName.equals(laurelName)
                    && info.proc != null && !info.refused) {
                return info.proc;
            }
        }
        return null;
    }

    /** {@code callee}'s preconditions, conjoined, over the pending call's actual arguments. */
    private StmtExpr inlinedPreconditions(Procedure callee, TranslationContext.PendingPreconditionOf pending) {
        var actuals = new HashMap<String, AstNode<StmtExpr>>();
        int a = 0;
        for (var param : callee.inputs()) {
            String pn = param.name().text();
            if (pn.equals(LAUREL_SELF_BINDING) && pending.receiver() != null) {
                actuals.put(pn, pending.receiver());
            } else if (a < pending.args().size()) {
                actuals.put(pn, pending.args().get(a++));
            }
        }
        java.util.function.UnaryOperator<AstNode<StmtExpr>> subst = n ->
                n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local l
                        && actuals.containsKey(l.name().text())
                        ? actuals.get(l.name().text())
                        : n;
        AstNode<StmtExpr> acc = null;
        for (var c : callee.preconditions()) {
            var inst = LaurelTrees.map(c.condition(), subst);
            acc = acc == null ? inst : node(LaurelFactory.primitiveOp(new Operation.AndThen(), acc, inst),
                    inst.source());
        }
        return acc == null ? new StmtExpr.LiteralBool(true) : acc.val();
    }

    /**
     * Each library @Contract's instance methods as procedures of the library type's
     * sort, translated like a relocated source contract (`opaque` with the contract's
     * clauses as ensures), e.g. {@code IntPredicate.test} from an
     * {@code IntPredicateContract}. Calls bind to them through
     * InvokeTranslator.libraryContractInvoke. Library methods keep their bare names: a
     * library contract declares one canonical entry per name.
     */
    private void emitLibraryContracts() {
        for (var e : decls.libraryContracts.entrySet()) {
            String sort = resolver.libraryContractSort(e.getKey());
            var contract = e.getValue();
            src.uri = contract.uri();
            int ordinal = -1;
            for (var cm : contract.decl().cm().methods()) {
                ordinal++;
                String name = cm.methodName().stringValue();
                int flags = cm.flags().flagsMask();
                if (name.startsWith("<") || cm.code().isEmpty()
                        || (flags & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE)) != 0) {
                    continue;
                }
                boolean isStatic = (flags & java.lang.classfile.ClassFile.ACC_STATIC) != 0;
                String owner = isStatic ? contract.fqn() : sort;
                String laurelName = name + resolver.overloadSuffix(contract.fqn(), cm);
                var info = new MethodGraph.MethodInfo(owner, name, laurelName, isStatic,
                        src.uri, Reporter.NO_RANGE, ordinal, true);
                graph.methodInfos.put(info.key(), info);
                translatingRelocatedContract = true;
                try {
                    translateMethod(owner, contract.decl().cm(), cm, info, false, false);
                } catch (JavaViolationException ex) {
                    info.refused = true;
                    info.cascadeEvenIfQuiet = true;
                } finally {
                    translatingRelocatedContract = false;
                }
            }
        }
        src.uri = null;
    }

    /** Set only while {@link #relocateFromContract} translates a contract method. */
    private boolean translatingRelocatedContract;

    /**
     * Translate a bodyless contractee method from its relocated source contract: the
     * contract method's body moves onto it, and it is treated as
     * {@code @Verify(false)}. The contract method is matched by name AND erased
     * descriptor, which is the signature a contract must repeat. Its own class model is
     * the one handed to the translator, because the lambda bodies of its clauses
     * (synthetic {@code lambda$} methods) live there; `this` and its fields are
     * redirected to the contractee through {@code ClassResolver.contracteeOf}.
     *
     * @return whether the method was handled here
     */
    private boolean relocateFromContract(Declarations.TypeNode node, java.lang.classfile.MethodModel m,
                                         String fqn, String name, boolean isStatic, FileRange mRange,
                                         int ordinal) {
        var contract = decls.sourceContracts.get(fqn);
        if (contract == null) {
            return false;
        }
        java.lang.classfile.MethodModel contractMethod = null;
        for (var cmth : contract.cm().methods()) {
            if (cmth.methodName().stringValue().equals(name)
                    && cmth.methodTypeSymbol().equals(m.methodTypeSymbol())
                    && cmth.code().isPresent()
                    && (cmth.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                            | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0) {
                contractMethod = cmth;
                break;
            }
        }
        if (contractMethod == null) {
            return false;
        }
        var info = new MethodGraph.MethodInfo(fqn, name,
                name + resolver.overloadSuffix(fqn, m), isStatic,
                src.uri, src.pointOf(mRange), ordinal, true);
        graph.methodInfos.put(info.key(), info);
        translatingRelocatedContract = true;
        try {
            translateMethod(fqn, contract.cm(), contractMethod, info, false, false);
        } catch (ReportedViolation e) {
            info.refused = true;
            recordRefusedStatus(info, name, mRange);
        } catch (JavaViolationException e) {
            info.refused = true;
            reportRefusal(info, e.getMessage());
            recordRefusedStatus(info, name, mRange);
        } finally {
            translatingRelocatedContract = false;
        }
        return true;
    }

    private void recordRefusedStatus(MethodGraph.MethodInfo info, String name, FileRange range) {
        var status = new MethodVerificationStatus(name,
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                MethodVerificationStatus.VerificationStatus.Skipped);
        info.status = status;
        recordStatus(range, status);
    }

    /** Refusal detection for a body-less method (signature and overload checks only). */
    private void probeBodylessMethod(Declarations.TypeNode node, java.lang.classfile.MethodModel m,
                                           boolean verify) {
        String name = m.methodName().stringValue();
        try {
            // Method-level parameters are kept separate: they may become TVars,
            // so this precheck must not refuse them or the method would be
            // demoted before translateMethod ever runs.
            var ownTypeParams = new java.util.LinkedHashSet<String>();
            m.findAttribute(java.lang.classfile.Attributes.signature()).ifPresent(sa -> {
                try {
                    java.lang.classfile.MethodSignature.parseFrom(sa.signature().stringValue())
                            .typeParameters().forEach(tp -> ownTypeParams.add(tp.identifier()));
                } catch (RuntimeException ignore) {
                    // no method type parameters
                }
            });
            var typeParams = new java.util.HashSet<String>(ownTypeParams);
            typeParams.addAll(Declarations.typeParamNamesOf(node.cm()));
            var mt = m.methodTypeSymbol();
            java.lang.classfile.MethodSignature msig =
                    m.findAttribute(java.lang.classfile.Attributes.signature())
                            .map(sa -> {
                                try {
                                    return java.lang.classfile.MethodSignature
                                            .parseFrom(sa.signature().stringValue());
                                } catch (RuntimeException e) {
                                    return null;
                                }
                            })
                            .orElse(null);
            boolean sigArgs = msig != null && msig.arguments().size() == mt.parameterCount();
            for (int i = 0; i < mt.parameterCount(); i++) {
                TypeRef t = null;
                if (sigArgs) {
                    try {
                        t = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries
                                .renderSig(msig.arguments().get(i)), false, false);
                    } catch (RuntimeException ignore) {
                        // fall back to the descriptor
                    }
                }
                if (t == null) {
                    t = TypeRef.ofDescriptor(mt.parameterType(i).descriptorString(), false, false);
                }
                t.toLaurel(typeParams, ownTypeParams, resolver, composites);
            }
            String retDesc = mt.returnType().descriptorString();
            if (!retDesc.equals("V")) {
                TypeRef rt = null;
                if (msig != null) {
                    try {
                        rt = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries
                                .renderSig(msig.result()), false, false);
                    } catch (RuntimeException ignore) {
                        // fall back to the descriptor
                    }
                }
                if (rt == null) {
                    rt = TypeRef.ofDescriptor(retDesc, false, false);
                }
                rt.toLaurel(typeParams, ownTypeParams, resolver, composites);
            }
            if (!m.flags().has(java.lang.reflect.AccessFlag.STATIC) && !name.startsWith("<")) {
                var inputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
                inputs.add(new org.strata.jverify.laurel.Parameter(ident(LAUREL_SELF_BINDING), node(selfType(node.fqn()))));
                for (int i = 0; i < mt.parameterCount(); i++) {
                    TypeRef t = null;
                    if (sigArgs) {
                        try {
                            t = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries
                                    .renderSig(msig.arguments().get(i)), false, false);
                        } catch (RuntimeException ignore) {
                            t = null;
                        }
                    }
                    if (t == null) {
                        t = TypeRef.ofDescriptor(mt.parameterType(i).descriptorString(), false, false);
                    }
                    inputs.add(new org.strata.jverify.laurel.Parameter(ident("p" + i),
                            node(t.toLaurel(typeParams, ownTypeParams, resolver, composites))));
                }
                var outputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
                if (!retDesc.equals("V")) {
                    TypeRef rt = null;
                    if (msig != null) {
                        try {
                            rt = TypeRef.ofRendered(org.strata.java.bytecode.frontend.ClassfileQueries
                                    .renderSig(msig.result()), false, false);
                        } catch (RuntimeException ignore) {
                            rt = null;
                        }
                    }
                    if (rt == null) {
                        rt = TypeRef.ofDescriptor(retDesc, false, false);
                    }
                    outputs.add(new org.strata.jverify.laurel.Parameter(LaurelFactory.reservedIdent(LAUREL_RESULT_BINDING),
                            node(rt.toLaurel(typeParams, ownTypeParams, resolver, composites))));
                }
                String laurelName = name + resolver.overloadSuffix(node.fqn(), m);
                composites.referencedCompositeTypes.add(node.fqn());
                var declPosts = List.<Condition>of();
                composites.instanceProcsBySort.computeIfAbsent(node.fqn(), k -> new ArrayList<>()).add(new Procedure(
                        ident(laurelName), ownTypeParams.stream().map(LaurelFactory::ident).toList(), inputs, outputs,
                        List.of(), new CoroutineContracts.Regular(), Optional.empty(),
                        new Body.Opaque(declPosts, Optional.empty(), unguardedModifies(
                                retDesc.equals("V") ? List.of(noRange(new StmtExpr.All())) : List.of())),
                        Optional.empty(), false, List.of(), Optional.empty(), Optional.empty(), List.of(),
                        List.of(), List.of()));
            }
        } catch (JavaViolationException e) {
            var info = new MethodGraph.MethodInfo(node.fqn(), name,
                    name + resolver.overloadSuffix(node.fqn(), m),
                    m.flags().has(java.lang.reflect.AccessFlag.STATIC),
                    src.uri, Reporter.NO_RANGE, 0, !verify);
            info.refused = true;
            graph.methodInfos.putIfAbsent(info.key(), info);
            reportRefusal(info, e.getMessage());
        }
    }

    /**
     * Register a not-verified status for every declared method that was not
     * translated: -Xjcov's CRT (written only for source constructs) decides
     * explicit-vs-implicit constructors.
     */
    private void registerUntranslatedMethods(ClassSource cls) {
        if (src.uri == null) {
            return;
        }
        var models = new ArrayList<java.lang.classfile.ClassModel>();
        models.add(cls.classModel());
        models.addAll(cls.nestedModels());
        var tree = methodStatuses.computeIfAbsent(src.uri, k -> new IntervalTree<>());
        for (var cm : models) {
            String modelFqn = JavaNames.dottedFqn(cm.thisClass().asInternalName());
            if (decls.contractClasses.contains(modelFqn)) {
                continue; // @Contract classes are spec definitions, not user code
            }
            var classAnno = verifyAnnotation(cm);
            boolean classOptedOut = classAnno != null
                    ? !verifyValue(classAnno, "value", true)
                    : !shouldVerify();
            for (var m : cm.methods()) {
                String name = m.methodName().stringValue();
                if (!name.equals("<init>")) {
                    continue;
                }
                if ((m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                        | java.lang.classfile.ClassFile.ACC_BRIDGE)) != 0) {
                    continue;
                }
                if (attemptedConstructors.contains(Constructors.procName(modelFqn, m.methodTypeSymbol()))) {
                    continue;
                }
                boolean crtExplicitCtor = m.code()
                        .map(code -> code.attributes().stream()
                                .anyMatch(a -> a instanceof
                                        java.lang.classfile.attribute.CharacterRangeTableAttribute crt
                                        && !crt.characterRangeTable().isEmpty()))
                        .orElse(false);
                // Without -Xjcov no ctor has CRT entries, so fall back to the
                // bytecode shape: parameters or code beyond the minimal
                // `aload_0; invokespecial <init>; return` mean source wrote
                // it (or field initializers exist -- which the CRT counts the
                // same way). An EMPTY explicit ctor is indistinguishable from
                // an implicit one here and counts Verified.
                boolean explicitCtor = org.strata.java.bytecode.frontend.CompilationFacts.of(cm).hasCrt()
                        ? crtExplicitCtor
                        : structuralExplicitCtor(cm, m);
                tree.insert(0, 0, new MethodVerificationStatus(name, Reporter.NO_RANGE,
                        explicitCtor || classOptedOut
                                ? MethodVerificationStatus.VerificationStatus.Skipped
                                : MethodVerificationStatus.VerificationStatus.Verified));
            }
        }
    }

    /**
     * CRT-free explicit-constructor detection, approximating "javac wrote
     * CRT entries for this ctor": source-declared parameters, or any
     * source-attributable instruction. Compiler-synthesized ctor plumbing is
     * discounted on both sides: the enclosing-instance parameter of a
     * non-static inner class (MethodParameters/ACC_MANDATED is only written
     * under {@code -parameters}, so it is detected from the InnerClasses
     * attribute + the first parameter's type), and the synthetic code
     * shapes (the super call, {@code this$}/{@code val$} field stores, the
     * {@code Objects.requireNonNull} enclosing-instance check).
     */
    static boolean structuralExplicitCtor(java.lang.classfile.ClassModel cm,
                                                  java.lang.classfile.MethodModel m) {
        String self = cm.thisClass().asInternalName();
        var mt = m.methodTypeSymbol();
        int declaredParams = mt.parameterCount();
        for (var ic : org.strata.java.bytecode.frontend.ClassfileQueries.innerClassesOf(cm)) {
            if (!ic.innerClass().asInternalName().equals(self)) {
                continue;
            }
            if (ic.innerName().isEmpty()) {
                return false;   // anonymous: a source ctor cannot exist
            }
            if ((ic.flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) == 0) {
                String outer = org.strata.java.bytecode.frontend.ClassfileQueries.directOuter(cm, ic);
                if (outer != null && declaredParams > 0
                        && mt.parameterType(0).descriptorString().equals("L" + outer + ";")) {
                    declaredParams--;   // the mandated enclosing instance
                }
            }
            break;
        }
        if (declaredParams > 0) {
            return true;
        }
        var code = m.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var el : code) {
            if (!(el instanceof java.lang.classfile.Instruction in)) {
                continue;
            }
            boolean synthetic = switch (in) {
                case java.lang.classfile.instruction.LoadInstruction l -> true;
                case java.lang.classfile.instruction.StackInstruction s -> true;
                case java.lang.classfile.instruction.ReturnInstruction r -> true;
                case java.lang.classfile.instruction.InvokeInstruction inv ->
                        (inv.opcode() == java.lang.classfile.Opcode.INVOKESPECIAL
                                && inv.name().stringValue().equals("<init>"))
                        || (inv.opcode() == java.lang.classfile.Opcode.INVOKESTATIC
                                && inv.owner().asInternalName().equals("java/util/Objects")
                                && inv.name().stringValue().equals("requireNonNull"));
                case java.lang.classfile.instruction.FieldInstruction fi ->
                        fi.opcode() == java.lang.classfile.Opcode.PUTFIELD
                                && (fi.name().stringValue().startsWith("this$")
                                        || fi.name().stringValue().startsWith("val$"));
                default -> false;
            };
            if (!synthetic) {
                return true;    // source-attributable code (field initializers included)
            }
        }
        return false;
    }
}
