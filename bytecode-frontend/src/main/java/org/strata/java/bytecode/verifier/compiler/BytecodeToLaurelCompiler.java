package org.strata.java.bytecode.verifier.compiler;

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

/**
 * Compiles JVM classfiles to Laurel. The bytecode-frontend mirror of JFE's
 * {@code JavaToLaurelCompiler} + {@code MethodOrLoopContractCompiler}: same
 * Laurel encodings (binary ops as {@code $op} StaticCalls,
 * constrained-integer prelude, cast helper procedures, loop label scheme),
 * but driven straight by the classfiles with CharacterRangeTable spans
 * instead of attributed javac trees.
 *
 * <p>Position fidelity: the CRT gives statement-level source ranges. The
 * whole statement's range (minus trailing {@code ;}) stamps the translated
 * statement, matching JFE's {@code sourceTreeFor}; contract clause ranges
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

    /** Positional service: current file text/index plus span-to-range helpers. */
    private final SourceMap src = new SourceMap();
    /** Laurel node builders and the cast-helper procedure registry. */
    private final LaurelFactory lf = new LaurelFactory(src);

    // Per-run state
    private final List<Procedure> procedures = new ArrayList<>();
    /** Composite-sort registry: referenced sorts, accessed fields, extends links, instance procs. */
    private final CompositeModel composites;
    /** The program's type declarations: type trees, @Contract/@Pure markers, method sets. */
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
    /** Synthesized name binding the thrown value in a `throws (e: T)` clause (JFE's THROWN_BINDING). */
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
        this.tierIsSelfInflicted = tierIsSelfInflicted;
        this.jvmVerifyInput = jvmVerifyInput;
        this.reporter = reporter;
        this.verifyByDefault = verifyByDefault;
        this.classPath = List.copyOf(classPath);
        this.resolver = new ClassResolver(classPath, verifyByDefault);
        this.composites = new CompositeModel(resolver);
        this.graph = new MethodGraph(reporter);
        shouldVerifies.push(verifyByDefault ? ShouldVerifyMode.DefaultYes : ShouldVerifyMode.DefaultNo);
    }

    public record AnalysisResult(Program program,
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

        // Pre-pass: collect the type trees, @Contract markers, @Pure markers,
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
        }

        new ContractValidator(reporter, src, decls, resolver).validate();

        for (var cls : classes) {
            src.uri = sourceUriFor(cls, sourceUris);
            compileUnit(cls, typeTrees.get(cls.internalName()));
        }

        // Refusal propagation (JFE's demoteCallersOfRefusedMethods): a method
        // that calls a refused one is itself demoted -- soundness, since its
        // callee's contract was never established. Iterated to a fixpoint so
        // A -> B -> C chains demote fully, then the demoted methods' emitted
        // procedures are dropped (an InstanceCall/StaticCall to an unemitted
        // procedure would die in Strata resolution).
        demoteCallersOfRefusedMethods();
        dropRefusedProcedures();

        // Composite declarations: every referenced class-typed sort together
        // with its full Java supertype closure (superclass chain + interfaces,
        // transitively including Object), each with only its accessed fields.
        // Port of JFE's emitComposite: every sort `extends` its DIRECT
        // supertypes and Strata walks those edges transitively, so a subtype
        // value flows to any supertype-typed position exactly as Java subtyping
        // allows. ANCESTORS FIRST: Laurel builds a composite's scope by copying
        // each parent's, so a parent must be declared before its children.
        List<TypeDefinition> types = new ArrayList<>(getPredefinedTypes());
        var emittedComposites = new java.util.HashSet<String>();
        for (var typeName : new ArrayList<>(composites.referencedCompositeTypes)) {
            emitCompositeClosure(typeName, emittedComposites, types);
        }
        List<Procedure> allProcedures = new ArrayList<>(lf.castHelpers.values());
        allProcedures.addAll(procedures);
        var program = new Program(allProcedures, List.of(), types, List.of());

        return new AnalysisResult(program,
                positionResolver(java.util.Set.copyOf(sourceUris)), methodStatuses);
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
        graph.demoteCallersOfRefusedMethods();
    }

    /**
     * Drop the emitted procedures of demoted methods (JFE's post-fixpoint
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
    private void collectDeclaredFields(String fqn, java.lang.classfile.ClassModel cm) {
        var fields = composites.declaredFields.computeIfAbsent(fqn, k -> new LinkedHashMap<>());
        for (var fm : cm.fields()) {
            int flags = fm.flags().flagsMask();
            if ((flags & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                    | java.lang.classfile.ClassFile.ACC_ENUM)) != 0) {
                continue;
            }
            boolean isNat = false;
            boolean isUnbounded = false;
            for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(fm)) {
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
                    t, (flags & java.lang.classfile.ClassFile.ACC_FINAL) != 0));
        }
    }

    // --- External @Contract validation (JFE's ExternalContractCompiler checks) ---

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
     * file (the behavior the per-file line map used to provide).
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

    // --- Contract extraction (mirror of MethodOrLoopContractCompiler) ---

    // --- Types ---

    // --- Exception hierarchy (JFE's superclassOf/isSubclassOf/LCA/linkExtends) ---

    /**
     * Resolve a class reference to an fqn: this compilation's
     * classes first, then the unit's single-type imports, then an already-
     * qualified name as-is, then {@code java.lang}.
     */
    private String resolveClassFqn(String name) {
        return resolver.resolveClassFqn(name);
    }

    /** The Laurel sort name of {@code java.lang.Object} (JFE's OBJECT_SORT). */
    private static final String OBJECT_SORT = CompositeModel.OBJECT_SORT;

    /**
     * Emit composite {@code sortName} and its full supertype closure, ANCESTORS
     * FIRST (parents before children, as Laurel requires for scope copying).
     * Mirror of JFE's {@code emitComposite}: each sort {@code extends} its
     * direct supertypes (superclass + interfaces); a sort with no supertype
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
     * loudly (JFE's linkExtends).
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
     * linked (JFE's registerHierarchyUpToThrowable).
     */
    private void registerHierarchyUpToThrowable(String fqn) {
        composites.registerHierarchyUpToThrowable(fqn);
    }

    // --- Per-class compilation ---

    // --- @Verify stack (JFE's VerifyAnnotationCompiler modes) ---

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
     * Report a method's translation refusal with JFE's severity asymmetry: a
     * static method's failure is an error ({@code translatorError={0}}), an
     * instance method's a warning ({@code instanceMethodNotVerified}) --
     * instance support is deliberately partial, so a refusal demotes to an
     * honest Skipped instead of failing the build. Both anchor at the refused
     * method's own name (JFE's JCMethodDecl preferred position). The
     * diagnostic is deduped per (method, reason); the demotion never is.
     */
    private void reportRefusal(MethodGraph.MethodInfo info, String reason) {
        graph.reportRefusal(info, reason);
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
     * JFE's declaration-site overloadRefusal: refuse any method whose class
     * declares more than one non-synthetic, non-bridge method of the same
     * name (checked against the classfile's method table, which is exactly
     * what by-name Laurel resolution would see). Statics and instance
     * methods live in different Laurel namespaces (Class?name vs the
     * composite), so only same-staticness siblings collide; opted-out
     * methods emit no procedure, so they cannot collide either.
     */
    private void refuseIfOverloaded(String classFqn, String name, boolean isStatic) {
        resolver.refuseIfOverloaded(classFqn, name, isStatic);
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

    // --- Switch desugaring (JFE's SwitchDesugarer: arrow switches -> if cascade) ---

    // --- Loops (label scheme mirrors JFE's emitLoop) ---

    // --- Expressions ---

    // --- Casts (JFE's cast-helper scheme) ---

    /** Fully qualify a type's printed name against this compilation's classes (JFE prints fqns). */
    private String qualifyTypeName(String name) {
        return resolver.qualifyTypeName(name);
    }

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
        }
        return translationCtx;
    }

    /**
     * One direct body run: the structured top-level statements + the side
     * tables. A conversion refusal is CAPTURED with the partial body (its
     * spec markers drive the contract refusal pass): contract-clause
     * refusals beat body-statement refusals, as in JFE.
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
        b.retDesc = shape.retDesc();
        b.isInstance = !shape.isStatic();
        return b;
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
     * statements (JFE converts the header first), so a header clause's
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

    /** A spec marker outside the contract header refuses (JFE's message). */
    private void checkNoSpecMarkers(AstNode<StmtExpr> n, BodyTranslator body) {
        if (body.specStmts.containsKey(n)) {
            throw new JavaViolationException(
                    "call to contract method must come before the rest of the body");
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            checkNoSpecMarkers(c, body);
        }
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
            // A spec describes the method, not its runtime effect (JFE's
            // spec-subtree exclusion): check/assume subtrees don't count.
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
            default -> {
            }
        }
        String key = body.calleeKeys.get(n.val());
        if (key != null && !decls.isPure(key)) {
            return true;
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            if (heapNode(c, body)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLoop(List<AstNode<StmtExpr>> implementation) {
        for (var stmt : implementation) {
            if (containsWhile(stmt)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhile(AstNode<StmtExpr> n) {
        if (n.val() instanceof StmtExpr.While) {
            return true;
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            if (containsWhile(c)) {
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

    /** A case's {@code .postcondition(...)} property (JFE's convertCasePostcondition). */
    private Condition convertCasePostcondition(BodyTranslator.SpecLink link, BodyTranslator body,
                                                     FileRange range, TranslationContext ctx) {
        var handle = link.lambdaArg();
        if (handle == null) {
            // A functional-interface cast may wrap the lambda (JFE unwraps
            // parens and casts here).
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
        var converted = body.coerce(
                body.translateLambdaBody(handle, Map.of(binders.get(0).name(), THROWN_BINDING)), "Z");
        return new Condition(LaurelTrees.rerange(converted, range, ctx.method.statementNodes),
                Optional.empty(), new ConditionMode.Both());
    }

    /**
     * Translate one method into a Laurel procedure: signature (params,
     * return, throws join), contract header (preconditions, postconditions,
     * modifies, throwsOn cases), body via BodyTranslator/ControlFlowStructurer,
     * body-variant selection (Transparent/Opaque/Abstract), and the
     * verification-status record. Mirrors JFE's translateMethod pipeline.
     */
    private void translateMethod(String classFqn, java.lang.classfile.ClassModel cm,
                                       java.lang.classfile.MethodModel m,
                                       MethodGraph.MethodInfo info,
                                       boolean verify, boolean isInterpretEntry) {
        boolean isStatic = m.flags().has(java.lang.reflect.AccessFlag.STATIC);
        String bareName = m.methodName().stringValue();
        String methodName = isStatic ? classFqn + "_" + bareName : bareName;
        refuseIfOverloaded(classFqn, bareName, isStatic);

        var mt = m.methodTypeSymbol();
        if (isInterpretEntry && mt.parameterCount() > 0) {
            throw new JavaViolationException(
                    "@InterpretEntry method '" + bareName + "' must take no arguments");
        }

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
        var ctx = translationContext();
        ctx.currentClassFqn = classFqn;
        ctx.typeParams = typeParams;
        ctx.methodTypeParams = methodTypeParams;
        ctx.beginMethod(info);

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

        // --- Inputs (JFE's signature-refusal order: params, return, throws) ---
        var inputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
        if (!isStatic) {
            composites.referencedCompositeTypes.add(classFqn);
            inputs.add(new org.strata.jverify.laurel.Parameter(
                    ident(LAUREL_SELF_BINDING), node(new HighType.UserDefined(ident(classFqn)))));
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
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < paramCount; i++) {
            String desc = mt.parameterType(i).descriptorString();
            var ps = shape.locals().at(slot, 0);
            String pname = ps != null ? ps.name() : shape.slotName().getOrDefault(slot, "v" + slot);
            LaurelFactory.refuseReservedName(pname);
            // ACC_VARARGS: the declaration reads `T...`, so the COMPONENT
            // type is what translates (JFE sees the source `T`).
            boolean va = varargs && i == paramCount - 1 && desc.startsWith("[");
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
            inputs.add(new org.strata.jverify.laurel.Parameter(ident(pname),
                    node(t.toLaurel(typeParams, methodTypeParams, resolver, composites))));
            slot += (desc.equals("J") || desc.equals("D")) ? 2 : 1;
        }

        // --- Outputs ---
        var outputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
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
            outputs.add(new org.strata.jverify.laurel.Parameter(LaurelFactory.reservedIdent(LAUREL_RESULT_BINDING),
                    node(rt.toLaurel(typeParams, methodTypeParams, resolver, composites))));
            if (rt.kind() == TypeRef.Kind.CLASS) {
                ctx.method.returnSort = qualifyTypeName(rt.nameWithScope());
            }
        }

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
        ThrowCoveragePrescan.check(shape, ctx);

        BodyRun run = runBody(cm, shape, ctx);
        if (!run.body().todos.isEmpty()) {
            throw new JavaViolationException(
                    "unsupported control flow (decompiler could not structure: "
                            + run.body().todos.get(0).strip() + ")");
        }
        if (run.refusal() != null) {
            // Old ordering: contract-header conversion refusals fire before
            // body-statement refusals.
            contractRefusalPass(run.body(), ctx);
            throw run.refusal();
        }
        var topStmts = run.topStmts();
        var body = run.body();

        // --- Contract header scan (JFE's getContract, over the spec markers) ---
        var pres = new ArrayList<AstNode<StmtExpr>>();
        var posts = new ArrayList<AstNode<StmtExpr>>();
        var invs = new ArrayList<AstNode<StmtExpr>>();
        var mods = new ArrayList<AstNode<StmtExpr>>();
        var throwsOns = new ArrayList<AstNode<StmtExpr>>();
        int hdr = 0;
        for (; hdr < topStmts.size(); hdr++) {
            var spec = body.specStmts.get(topStmts.get(hdr));
            if (spec == null) {
                break;
            }
            switch (spec.name()) {
                case "precondition" -> pres.add(topStmts.get(hdr));
                case "postcondition" -> posts.add(topStmts.get(hdr));
                case "invariant" -> invs.add(topStmts.get(hdr));
                case "decreases", "reads" -> {
                    // recognized so they don't break the header scan; dropped
                    // at Laurel emission (JFE parity)
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

        var preconditions = new ArrayList<Condition>();
        for (var stmt : pres) {
            var spec = body.specStmts.get(stmt);
            if (spec.args().size() != 1) {
                throw new JavaViolationException(
                        "A call to 'precondition' may have only one argument");
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
            converted = body.coerce(converted, "Z");
            preconditions.add(new Condition(
                    LaurelTrees.rerange(converted, condRange, ctx.method.statementNodes),
                    Optional.empty(), new ConditionMode.Both()));
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
                        : Map.of();
                converted = body.translateLambdaBody(spec.lambdaArg(), ren);
            } else {
                converted = spec.args().get(0);
                body.checkContractArg(converted);
            }
            converted = body.coerce(converted, "Z");
            postconditions.add(new Condition(
                    LaurelTrees.rerange(converted, condRange, ctx.method.statementNodes),
                    Optional.empty(), new ConditionMode.Both()));
        }

        var explicitModifies = new ArrayList<AstNode<StmtExpr>>();
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
            } else {
                body.checkContractArg(arg);
                explicitModifies.add(LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes));
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
                            caseModifies.add(LaurelTrees.rerange(arg, stmtRange,
                                    ctx.method.statementNodes));
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
        for (int i = hdr; i < topStmts.size(); i++) {
            checkNoSpecMarkers(topStmts.get(i), body);
            implementation.add(topStmts.get(i));
        }
        StmtExpr methodBody = new StmtExpr.Block(implementation, Optional.empty());

        // --- Body-variant selection (mirrors JFE's removeImplementation rules) ---
        boolean isPure = hasAnnotation(m, "Pure");
        List<AstNode<StmtExpr>> modifies = !explicitModifies.isEmpty()
                ? explicitModifies
                : writesHeap(implementation, body)
                        ? List.of(noRange(new StmtExpr.All()))
                        : List.of();
        Body procBody;
        if (!verify) {
            procBody = postconditions.isEmpty()
                    ? new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()))
                    : new Body.Abstract(postconditions);
        } else if (isPure && containsLoop(implementation)) {
            if (!postconditions.isEmpty()) {
                throw new JavaViolationException(
                        "@Pure function with a loop and a postcondition is not yet supported");
            }
            procBody = new Body.Opaque(List.of(), Optional.empty(), unguardedModifies(List.of()));
        } else if (isPure && postconditions.isEmpty() && modifies.isEmpty()
                && throwsType.isEmpty()) {
            // A declared `throws` forces Opaque: Strata rejects a transparent procedure
            // whose body throws, because a transparent body becomes a function and
            // throwing is not expressible as an expression. Gating on the declaration
            // rather than on whether the body actually throws is deliberately
            // conservative -- it costs transparency only for a `@Pure` method that
            // declares `throws` without ever throwing.
            procBody = new Body.Transparent(noRange(methodBody));
        } else {
            procBody = new Body.Opaque(postconditions, Optional.of(noRange(methodBody)),
                    unguardedModifies(modifies));
        }
        var proc = new Procedure(
                ident(methodName),
                methodTypeParams.stream().map(LaurelFactory::ident).toList(),
                inputs, outputs, preconditions,
                Optional.empty(), procBody, Optional.empty(),
                isInterpretEntry, List.of(),
                throwsType, throwsBinding, throwsOnBlocks);
        if (isStatic) {
            procedures.add(proc);
        } else {
            composites.instanceProcsBySort.computeIfAbsent(classFqn, k -> new ArrayList<>()).add(proc);
        }
        var range = src.methodRangeOf(shape.crt());
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
                verify ? MethodVerificationStatus.VerificationStatus.Verified
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

    private void compileUnit(ClassSource cls, Declarations.TypeNode root) {
        // The direct path resolves class references from descriptors and the
        // classfile pool; no import table exists or is needed.
        resolver.imports = Map.of();
        compileType(root);
        registerUntranslatedMethods(cls);
    }

    private void warnMissingSuperInitContract(Declarations.TypeNode node) {
        String superFqn = resolver.programSuper.get(node.fqn());
        if (superFqn == null || decls.programTypes.containsKey(superFqn)
                || !isThrowableSubtype(superFqn)) {
            return;
        }
        // Anchor at this class's declaration line (the earliest member line,
        // per MethodBodyFactory.classDeclLine), where the source frontend
        // reports it.
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
            for (var nested : node.nested()) {
                if (nested.anonymous()) {
                    // A hoisted ANONYMOUS class is deferred exactly as JFE
                    // defers it: methods demote to Skipped, no diagnostic.
                    for (var m : Declarations.declaredMethodsOf(nested.cm())) {
                        if (m.code().isPresent()) {
                            var range = src.methodCrtRange(m);
                            recordStatus(range, new MethodVerificationStatus(
                                    m.methodName().stringValue(),
                                    range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                                    MethodVerificationStatus.VerificationStatus.Skipped));
                        }
                    }
                    continue;
                }
                compileType(nested);
            }
        } finally {
            shouldVerifies.pop();
        }
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
        if (m.code().isEmpty()) {
            if (hasAnnotation(m, "EmptyContract")) {
                recordStatus(mRange, new MethodVerificationStatus(name,
                        mRange == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(mRange),
                        verifiedOrSkipped));
                return;
            }
            probeBodylessMethod(node, m, verify);
            return;
        }
        var info = new MethodGraph.MethodInfo(fqn, name, isStatic,
                src.uri, src.pointOf(mRange), ordinal, !verify);
        graph.methodInfos.put(info.key(), info);
        try {
            translateMethod(fqn, node.cm(), m, info, verify, isInterpretEntry);
        } catch (ReportedViolation e) {
            info.refused = true;
            recordRefusedStatus(info, name, mRange);
        } catch (JavaViolationException e) {
            info.refused = true;
            reportRefusal(info, e.getMessage());
            recordRefusedStatus(info, name, mRange);
        }
    }

    private void recordRefusedStatus(MethodGraph.MethodInfo info, String name, FileRange range) {
        var status = new MethodVerificationStatus(name,
                range == UNKNOWN_RANGE ? Reporter.NO_RANGE : positionRange(range),
                MethodVerificationStatus.VerificationStatus.Skipped);
        info.status = status;
        recordStatus(range, status);
    }

    /** JFE-parity refusal detection for a body-less method (the direct probeBodylessMethod). */
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
            refuseIfOverloaded(node.fqn(), name, m.flags().has(java.lang.reflect.AccessFlag.STATIC));
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
        } catch (JavaViolationException e) {
            var info = new MethodGraph.MethodInfo(node.fqn(), name,
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
    private static boolean structuralExplicitCtor(java.lang.classfile.ClassModel cm,
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
