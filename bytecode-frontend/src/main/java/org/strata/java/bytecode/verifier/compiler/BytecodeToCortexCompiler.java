package org.strata.java.bytecode.verifier.compiler;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.BytecodeFrontend.ClassSource;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.CrtRange;
import org.strata.java.bytecode.frontend.LineNumbers;
import org.strata.java.bytecode.frontend.MethodBodyFactory;
import org.strata.java.bytecode.frontend.Span;
import org.strata.java.bytecode.frontend.Trap;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;
import org.strata.java.bytecode.verifier.laurel.PositionResolver;
import org.strata.jverify.verifier.IntervalTree;
import org.strata.jverify.cortex.ArrayDecl;
import org.strata.jverify.cortex.Block;
import org.strata.jverify.cortex.Body;
import org.strata.jverify.cortex.Cmd;
import org.strata.jverify.cortex.Composite;
import org.strata.jverify.cortex.Expr;
import org.strata.jverify.cortex.Field;
import org.strata.jverify.cortex.FileRange;
import org.strata.jverify.cortex.Global;
import org.strata.jverify.cortex.Handler;
import org.strata.jverify.cortex.Param;
import org.strata.jverify.cortex.Procedure;
import org.strata.jverify.cortex.Program;
import org.strata.jverify.cortex.Raw;
import org.strata.jverify.cortex.SourceRange;
import org.strata.jverify.cortex.Transfer;
import org.strata.jverify.cortex.Ty;
import org.strata.jverify.cortex.Uri;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates bytecode straight to Cortex, Strata's unstructured IR, without
 * going through Laurel.
 *
 * <p>Each basic block of a method's control-flow graph becomes a Cortex block,
 * and the exception table becomes Cortex's handler table, which also maps
 * protected blocks to handler blocks, tried in order. The operand stack is
 * kept in typed locals {@code s<depth><kind>} and the JVM locals in
 * {@code l<slot><kind>}, so every instruction is one or more commands over
 * named values and no expression is ever rebuilt. A value of the JVM's
 * {@code int} computational type is a {@code bv32} and a {@code long} a
 * {@code bv64}, so arithmetic wraps as it does on the JVM. The operations
 * that can fail (overflow, division, narrowing) are calls to Strata's Cortex
 * Java prelude ({@code java$iadd} and so on); a failed cast throws a fresh
 * {@code ClassCastException}.
 *
 * <p>When interpreting, a one-dimensional array of {@code boolean},
 * {@code byte}, {@code char}, {@code short}, {@code int} or {@code long} is an
 * object of a Cortex array type {@code $Array$<element>} that extends
 * {@code java.lang.Object}, and the array instructions are Cortex's array
 * commands. Cortex indexes an array with mathematical integers and leaves a
 * negative length and an index out of range undefined, so a check that throws
 * {@code NegativeArraySizeException} or
 * {@code ArrayIndexOutOfBoundsException} precedes each of them.
 *
 * <p>What the translation does not support it refuses, per method, with an
 * error: JVerify specifications (every {@code JVerify} call but {@code check},
 * {@code assume} and {@code implies}, and the specification annotations such
 * as {@code @Transparent}), {@code @Nat} and {@code @Unbounded}, {@code null}
 * literals and null tests, floating point, fields of library classes, class
 * literals, {@code invokedynamic} and monitors; arrays of references,
 * multi-dimensional arrays, arrays in static fields and, when verifying, every
 * array; and, when interpreting, calls to library methods, {@code clone} on an
 * array included.
 *
 * <p>A static field is a global variable named {@code owner:field}, holding
 * the JVM default of its type when an entry procedure starts and an arbitrary
 * value when any other procedure starts. A class's {@code <clinit>} is a
 * procedure, and a boolean global per class, {@code owner:<initialized>},
 * records whether it has run: at a {@code new}, a static field access or a
 * static method call on the class that declares the member, the translation
 * branches on the flag and, if it is false, sets it and calls the
 * initializer, as the JVM does the first time a class is used
 * ({@link ClassInitialization} has the rules). An exception that escapes the
 * initializer reaches the use as an {@code ExceptionInInitializerError}
 * unless it is an {@code Error}, a second flag marks the class erroneous, and
 * every later use throws {@code NoClassDefFoundError}. An entry procedure
 * initializes its own class first. A static field of a library class is a
 * global with an arbitrary value.
 */
public final class BytecodeToCortexCompiler {

    public record AnalysisResult(Program program, PositionResolver positions,
                                 Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses,
                                 StaticEffectNotes staticNotes) {
    }

    static final class Refusal extends RuntimeException {
        final @Nullable FileRange at;

        Refusal(String message, @Nullable FileRange at) {
            super(message);
            this.at = at;
        }
    }

    private enum Kind {
        I, J, A;

        Ty stackTy() {
            return switch (this) {
                case I -> new Ty.Bv(32);
                case J -> new Ty.Bv(64);
                case A -> new Ty.Named(OBJECT);
            };
        }
    }

    static final String OBJECT = "java.lang.Object";
    static final String JVERIFY = "org.strata.jverify.JVerify";
    static final String RESULT = "result";
    static final String PRELUDE = "java";
    static final String ARRAY = "$Array$";
    static final String ARITHMETIC_EXCEPTION = "java.lang.ArithmeticException";
    static final String RUNTIME_EXCEPTION = "java.lang.RuntimeException";
    static final FileRange NO_LOCATION = new FileRange(new Uri(""), new SourceRange(new Raw(0), new Raw(0)));

    private final Reporter reporter;
    private final ClassResolver resolver;
    private final boolean interpret;
    private final SourceMap src = new SourceMap();
    private final Map<URI, IntervalTree<Integer, MethodVerificationStatus>> methodStatuses = new HashMap<>();
    private final Map<String, ClassModel> programClasses = new LinkedHashMap<>();
    private final Set<String> referenced = new LinkedHashSet<>();
    private final Map<String, Procedure> procedures = new LinkedHashMap<>();
    /** The globals of static fields and initialization flags, by name, in first-use order. */
    private final Map<String, Global> globals = new LinkedHashMap<>();
    private final StaticEffectNotes staticNotes = new StaticEffectNotes();
    /** Each procedure's source file and verification status, for {@link #refuseUsersOfUntranslatedInitializers}. */
    private final Map<String, URI> uriOf = new HashMap<>();
    private final Map<String, MethodVerificationStatus> statusOf = new HashMap<>();
    /** The refusal of each {@code <clinit>} that was not translated, by procedure name. */
    private final Map<String, String> initializerRefusals = new HashMap<>();
    static final String INIT_FLAG = "<initialized>";
    static final String INIT_FAILED_FLAG = "<initFailed>";
    static final String THROWABLE = "java.lang.Throwable";
    static final String ERROR = "java.lang.Error";
    static final String EXCEPTION_IN_INITIALIZER_ERROR = "java.lang.ExceptionInInitializerError";
    static final String NO_CLASS_DEF_FOUND_ERROR = "java.lang.NoClassDefFoundError";
    public static final String CLINIT = "<clinit>";
    static final MethodTypeDesc CLINIT_DESC = MethodTypeDesc.of(ClassDesc.ofDescriptor("V"));
    private final Map<String, ArrayDecl> arrayTypes = new LinkedHashMap<>();

    public BytecodeToCortexCompiler(Reporter reporter, boolean verifyByDefault, List<Path> classPath,
                                    boolean interpret) {
        this.reporter = reporter;
        this.interpret = interpret;
        this.resolver = new ClassResolver(classPath, verifyByDefault);
    }

    public BytecodeToCortexCompiler withJavacTypes(
            Map<String, org.strata.java.bytecode.frontend.TypeFacts> types) {
        resolver.useJavacTypes(types);
        return this;
    }

    public AnalysisResult analyze(Collection<URI> sourceUris, List<ClassSource> classes) {
        var units = new ArrayList<ClassSource>();
        for (var cls : classes) {
            if (!cls.internalName().equals("package-info") && !cls.internalName().endsWith("/package-info")) {
                units.add(cls);
            }
        }
        for (var cls : units) {
            for (var cm : modelsOf(cls)) {
                String fqn = fqnOf(cm);
                programClasses.put(fqn, cm);
                resolver.programClassModels.put(fqn, cm);
                resolver.knownClasses.put(fqn, fqn);
                cm.superclass().ifPresent(sup -> resolver.programSuper.put(fqn, JavaNames.dottedFqn(sup.asInternalName())));
            }
        }
        for (var cls : units) {
            src.uri = SourceMap.sourceUriFor(cls, sourceUris);
            for (var cm : modelsOf(cls)) {
                compileClass(cm);
            }
        }
        addInheritedForwarders();
        refuseUsersOfUntranslatedInitializers();
        var program = new Program(composites(), List.of(), List.copyOf(procedures.values()),
                List.copyOf(globals.values()), List.of(PRELUDE), List.copyOf(arrayTypes.values()));
        var knownUris = new java.util.HashSet<URI>(sourceUris);
        return new AnalysisResult(program, BytecodeToLaurelCompiler.positionResolver(Set.copyOf(knownUris)),
                methodStatuses, staticNotes);
    }

    private static List<ClassModel> modelsOf(ClassSource cls) {
        var models = new ArrayList<ClassModel>();
        models.add(cls.classModel());
        models.addAll(cls.nestedModels());
        return models;
    }

    private static String fqnOf(ClassModel cm) {
        return JavaNames.dottedFqn(cm.thisClass().asInternalName());
    }

    // --- Composites ---

    private List<Composite> composites() {
        var out = new LinkedHashMap<String, Composite>();
        var work = new ArrayDeque<>(referenced);
        work.add(OBJECT);
        while (!work.isEmpty()) {
            String c = work.poll();
            if (out.containsKey(c)) {
                continue;
            }
            var parents = new ArrayList<String>();
            if (!c.equals(OBJECT)) {
                String sup = resolver.superclassOf(c);
                if (sup != null) {
                    parents.add(sup);
                }
                for (String i : resolver.directInterfacesOf(c)) {
                    parents.add(i);
                }
                if (parents.isEmpty()) {
                    parents.add(OBJECT);
                }
            }
            out.put(c, new Composite(c, parents, fieldsOf(c)));
            work.addAll(parents);
            for (String r : referenced) {
                if (!out.containsKey(r)) {
                    work.add(r);
                }
            }
        }
        return List.copyOf(out.values());
    }

    private List<Field> fieldsOf(String fqn) {
        var cm = programClasses.get(fqn);
        if (cm == null) {
            return List.of();
        }
        var fields = new ArrayList<Field>();
        for (var f : cm.fields()) {
            if (f.flags().has(AccessFlag.STATIC)) {
                continue;
            }
            var ty = fieldTy(f.fieldTypeSymbol());
            if (ty != null) {
                fields.add(new Field(f.fieldName().stringValue(), ty));
            }
        }
        return fields;
    }

    private @Nullable Ty fieldTy(ClassDesc cd) {
        try {
            return tyOf(cd, null);
        } catch (Refusal r) {
            return null;
        }
    }

    // --- Types and names ---

    private Ty tyOf(ClassDesc cd, @Nullable FileRange at) {
        if (cd.isArray()) {
            return new Ty.Named(arrayType(cd, at));
        }
        if (cd.isPrimitive()) {
            return switch (cd.descriptorString()) {
                case "Z", "B", "S", "C", "I" -> new Ty.Bv(32);
                case "J" -> new Ty.Bv(64);
                default -> throw new Refusal("not supported by the direct Cortex translation: floating point", at);
            };
        }
        String fqn = classFqn(cd);
        referenced.add(fqn);
        return new Ty.Named(fqn);
    }

    /**
     * The Cortex array type of a Java array type, declared on first use. Its elements are stored as
     * values of the JVM's computational type, as fields are.
     */
    private String arrayType(ClassDesc array, @Nullable FileRange at) {
        if (!interpret) {
            throw new Refusal("not supported by the direct Cortex translation: arrays", at);
        }
        ClassDesc elem = array.componentType();
        Ty stored = switch (elem.descriptorString()) {
            case "Z", "B", "S", "C", "I" -> new Ty.Bv(32);
            case "J" -> new Ty.Bv(64);
            default -> throw new Refusal("not supported by the direct Cortex translation: "
                    + (elem.isArray() ? "multi-dimensional arrays"
                    : elem.isPrimitive() ? "floating point" : "arrays of references"), at);
        };
        String name = ARRAY + elem.displayName();
        arrayTypes.putIfAbsent(name, new ArrayDecl(name, stored, List.of(OBJECT)));
        return name;
    }

    private static String classFqn(ClassDesc cd) {
        String d = cd.descriptorString();
        return JavaNames.dottedFqn(d.substring(1, d.length() - 1));
    }

    private static Kind kindOf(ClassDesc cd, @Nullable FileRange at) {
        if (cd.isArray() || !cd.isPrimitive()) {
            return Kind.A;
        }
        return switch (cd.descriptorString()) {
            case "Z", "B", "S", "C", "I" -> Kind.I;
            case "J" -> Kind.J;
            default -> throw new Refusal("not supported by the direct Cortex translation: floating point", at);
        };
    }

    private static Kind kindOf(TypeKind tk, @Nullable FileRange at) {
        return switch (tk) {
            case BOOLEAN, BYTE, SHORT, CHAR, INT -> Kind.I;
            case LONG -> Kind.J;
            case REFERENCE -> Kind.A;
            default -> throw new Refusal("not supported by the direct Cortex translation: floating point", at);
        };
    }

    static String methodKey(String name, MethodTypeDesc desc) {
        String base = name.equals("<init>") ? "$init" : name;
        if (desc.parameterCount() == 0) {
            return base;
        }
        var sb = new StringBuilder(base);
        for (var p : desc.parameterList()) {
            sb.append('$').append(ClassResolver.erasedTypeName(p).replace("[]", "Arr"));
        }
        return sb.toString();
    }

    static String procName(String owner, String name, MethodTypeDesc desc) {
        return owner + "$" + methodKey(name, desc);
    }

    private static boolean isSynthetic(MethodModel m) {
        return (m.flags().flagsMask() & (ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE)) != 0;
    }

    private static @Nullable MethodModel declared(ClassModel cm, String name, MethodTypeDesc desc, boolean isStatic) {
        for (var m : cm.methods()) {
            if (m.methodName().stringValue().equals(name)
                    && m.methodTypeSymbol().parameterList().equals(desc.parameterList())
                    && m.flags().has(AccessFlag.STATIC) == isStatic && !isSynthetic(m)) {
                return m;
            }
        }
        return null;
    }

    /** The program class on {@code owner}'s superclass chain that declares the method, or null. */
    private @Nullable String declaringClass(String owner, String name, MethodTypeDesc desc, boolean isStatic) {
        for (String c = owner; c != null && programClasses.containsKey(c); c = resolver.programSuper.get(c)) {
            if (declared(programClasses.get(c), name, desc, isStatic) != null) {
                return c;
            }
        }
        if (!isStatic && programClasses.containsKey(owner)) {
            var callee = resolver.findCallee(owner, name, desc);
            if (callee != null) {
                return callee.declaringFqn();
            }
        }
        return null;
    }

    private @Nullable String fieldOwner(String owner, String name) {
        for (String c = owner; c != null && programClasses.containsKey(c); c = resolver.programSuper.get(c)) {
            for (var f : programClasses.get(c).fields()) {
                if (f.fieldName().stringValue().equals(name) && !f.flags().has(AccessFlag.STATIC)) {
                    return c;
                }
            }
        }
        return null;
    }

    private List<Param> paramsOf(String owner, boolean isStatic, MethodTypeDesc desc, @Nullable FileRange at) {
        var params = new ArrayList<Param>();
        if (!isStatic) {
            referenced.add(owner);
            params.add(new Param("arg0", new Ty.Named(owner)));
        }
        for (var p : desc.parameterList()) {
            params.add(new Param("arg" + params.size(), tyOf(p, at)));
        }
        return params;
    }

    private List<Param> outputsOf(MethodTypeDesc desc, @Nullable FileRange at) {
        if (desc.returnType().descriptorString().equals("V")) {
            return List.of();
        }
        return List.of(new Param(RESULT, tyOf(desc.returnType(), at)));
    }

    private Optional<String> throwsOf(@Nullable ClassModel cm, MethodModel m) {
        var types = new ArrayList<String>();
        m.findAttribute(java.lang.classfile.Attributes.exceptions()).ifPresent(ex -> {
            for (var c : ex.exceptions()) {
                types.add(JavaNames.dottedFqn(c.asInternalName()));
            }
        });
        types.add(RUNTIME_EXCEPTION);
        String lca = types.getFirst();
        for (String t : types.subList(1, types.size())) {
            String c = resolver.commonSuperclass(lca, t);
            lca = c != null ? c : "java.lang.Throwable";
        }
        referenced.add(lca);
        return Optional.of(lca);
    }

    /** Forwards {@code C$m} to a superclass's {@code S$m} when an interface method of {@code C} is implemented only by {@code S}. */
    private void addInheritedForwarders() {
        for (var entry : List.copyOf(programClasses.entrySet())) {
            String c = entry.getKey();
            var supertypes = new java.util.LinkedHashSet<String>();
            var work = new ArrayDeque<String>();
            work.add(c);
            while (!work.isEmpty()) {
                String t = work.poll();
                String sup = resolver.superclassOf(t);
                if (sup != null && supertypes.add(sup)) {
                    work.add(sup);
                }
                for (String i : resolver.directInterfacesOf(t)) {
                    if (supertypes.add(i)) {
                        work.add(i);
                    }
                }
            }
            for (String iface : supertypes) {
                var im = programClasses.get(iface);
                if (im == null || !im.flags().has(AccessFlag.INTERFACE)) {
                    continue;
                }
                for (var m : im.methods()) {
                    if (m.flags().has(AccessFlag.STATIC) || m.flags().has(AccessFlag.PRIVATE) || isSynthetic(m)) {
                        continue;
                    }
                    String name = m.methodName().stringValue();
                    var desc = m.methodTypeSymbol();
                    if (declared(entry.getValue(), name, desc, false) != null) {
                        continue;
                    }
                    String s = null;
                    MethodModel sm = null;
                    for (String k = resolver.programSuper.get(c); k != null && programClasses.containsKey(k);
                            k = resolver.programSuper.get(k)) {
                        var km = declared(programClasses.get(k), name, desc, false);
                        if (km != null && !km.flags().has(AccessFlag.ABSTRACT) && !km.flags().has(AccessFlag.PRIVATE)) {
                            s = k;
                            sm = km;
                            break;
                        }
                    }
                    String proc = procName(c, name, desc);
                    if (s == null || resolver.isSubtypeOf(s, iface) || procedures.containsKey(proc)) {
                        continue;
                    }
                    List<Param> inputs;
                    List<Param> outputs;
                    try {
                        inputs = paramsOf(c, false, desc, NO_LOCATION);
                        outputs = outputsOf(desc, NO_LOCATION);
                    } catch (Refusal r) {
                        continue;
                    }
                    var args = new ArrayList<Expr>();
                    for (var p : inputs) {
                        args.add(new Expr.Var(p.name()));
                    }
                    var call = new Cmd.Call(outputs.isEmpty() ? List.of() : List.of(RESULT), procName(s, name, desc),
                            args, NO_LOCATION);
                    var body = new Body("entry", List.of(new Block("entry", List.of(call),
                            new Transfer.Finish(NO_LOCATION))), List.of());
                    procedures.put(proc, new Procedure(proc, inputs, outputs, throwsOf(programClasses.get(s), sm),
                            List.of(), Optional.of(body), false, NO_LOCATION));
                }
            }
        }
    }

    /** A bodiless procedure for a method the program does not define, declared on first call. */
    private String libraryProc(String owner, String name, MethodTypeDesc desc, boolean isStatic, FileRange at) {
        String proc = procName(owner, name, desc);
        if (!procedures.containsKey(proc)) {
            var cm = resolver.loadClassModel(owner);
            MethodModel m = cm == null ? null : declared(cm, name, desc, isStatic);
            procedures.put(proc, new Procedure(proc, paramsOf(owner, isStatic, desc, at), outputsOf(desc, at),
                    m == null ? Optional.of(RUNTIME_EXCEPTION) : throwsOf(null, m), List.of(), Optional.empty(),
                    false, at));
        }
        return proc;
    }

    // --- Ranges ---

    private FileRange cortexRange(org.strata.jverify.laurel.FileRange r) {
        if (r == SourceMap.UNKNOWN_RANGE) {
            return NO_LOCATION;
        }
        return new FileRange(new Uri(r.file().path()), new SourceRange(
                new Raw(r.range().start().byteIdx()), new Raw(r.range().stop().byteIdx())));
    }

    private org.strata.jverify.laurel.FileRange laurelRange(FileRange r) {
        return new org.strata.jverify.laurel.FileRange(new org.strata.jverify.laurel.Uri(r.file().path()),
                new org.strata.jverify.laurel.SourceRange(new org.strata.jverify.laurel.Raw(r.range().start().byteIdx()),
                        new org.strata.jverify.laurel.Raw(r.range().stop().byteIdx())));
    }

    // --- Java helpers ---

    // --- Classes and methods ---

    private void compileClass(ClassModel cm) {
        String fqn = fqnOf(cm);
        referenced.add(fqn);
        boolean isEnum = cm.flags().has(AccessFlag.ENUM);
        for (var m : cm.methods()) {
            String name = m.methodName().stringValue();
            if (isSynthetic(m) && !isDispatchBridge(cm, m) || name.equals(CLINIT)) {
                continue;
            }
            if (isEnum && (name.equals("values") || name.equals("valueOf") || name.equals("$values"))) {
                continue;   // javac's enum members, over the $VALUES array, which is not modelled
            }
            compileMethod(cm, fqn, m);
        }
        // <clinit> is translated like a static method; the guards emitted where
        // the class is first used call it.
        var clinit = ClassInitialization.initializerOf(resolver, fqn);
        if (clinit != null) {
            compileMethod(cm, fqn, clinit);
        }
    }

    // --- Static fields ---

    /** The global of static field {@code name} of {@code ownerFqn}; a single {@code :} occurs in no other emitted name. */
    static String globalName(String ownerFqn, String name) {
        return ownerFqn + ":" + name;
    }

    /** Declare the global of a static field of program class {@code ownerFqn} on first use and return its name. */
    private String declareGlobal(String ownerFqn, String name, ClassDesc type, FileRange at) {
        String global = globalName(ownerFqn, name);
        if (!globals.containsKey(global)) {
            Ty ty = tyOf(type, at);
            globals.put(global, new Global(global, ty, defaultOf(ty), at));
        }
        return global;
    }

    /**
     * Declare the global of a static field of a class outside the program.
     * Nothing is known about it: no initializer of the class is translated, so
     * it has no initializer and holds an arbitrary value, in an entry procedure
     * too, and the class has no initialization flag.
     */
    private String declareLibraryGlobal(String ownerFqn, String name, ClassDesc type, FileRange at) {
        String global = globalName(ownerFqn, name);
        if (!globals.containsKey(global)) {
            globals.put(global, new Global(global, tyOf(type, at), Optional.empty(), at));
        }
        return global;
    }

    private String declareInitFlag(String fqn, FileRange at) {
        return declareFlag(globalName(fqn, INIT_FLAG), at);
    }

    /** The flag that records that initializing {@code fqn} completed abruptly (JLS 12.4.2: the class is erroneous). */
    private String declareInitFailedFlag(String fqn, FileRange at) {
        return declareFlag(globalName(fqn, INIT_FAILED_FLAG), at);
    }

    private String declareFlag(String flag, FileRange at) {
        globals.computeIfAbsent(flag, f -> new Global(f, new Ty.Bool(), Optional.of(new Expr.Bool(false)), at));
        return flag;
    }

    /** The JVM default of a value of type {@code ty}: zero or false; none for a reference, which this translation has no null for. */
    private static Optional<Expr> defaultOf(Ty ty) {
        return switch (ty) {
            case Ty.Bool b -> Optional.of(new Expr.Bool(false));
            case Ty.Bv bv -> Optional.of(new Expr.Bv(bv.width(), 0));
            default -> Optional.empty();
        };
    }

    private int lineOf(FileRange at) {
        return src.positionRange(laurelRange(at)).start().line();
    }

    private static boolean isBridge(MethodModel m) {
        return (m.flags().flagsMask() & ClassFile.ACC_BRIDGE) != 0;
    }

    /**
     * A bridge whose parameter types differ from the method it forwards to: it is the override that
     * dispatch on the erased signature finds. A bridge that only narrows the return type has the
     * forwarded method's parameters, and so its procedure name, and is not translated.
     */
    private static boolean isDispatchBridge(ClassModel cm, MethodModel bridge) {
        if (!isBridge(bridge)) {
            return false;
        }
        var params = bridge.methodTypeSymbol().parameterList();
        return cm.methods().stream().noneMatch(m -> !isSynthetic(m)
                && m.methodName().equals(bridge.methodName())
                && m.methodTypeSymbol().parameterList().equals(params));
    }

    private void compileMethod(ClassModel cm, String fqn, MethodModel m) {
        String name = m.methodName().stringValue();
        var desc = m.methodTypeSymbol();
        boolean isStatic = m.flags().has(AccessFlag.STATIC);
        boolean verify = !resolver.isOptedOut(cm, m);
        var mRange = src.methodCrtRange(m);
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (mRange == SourceMap.UNKNOWN_RANGE && code != null) {
            var lines = LineNumbers.of(code);
            if (lines != null) {
                mRange = src.lineSpanRange(lines.minLine(), lines.maxLine());
            }
        }
        FileRange range = cortexRange(mRange);
        boolean entry = isStatic && desc.parameterCount() == 0 && Declarations.hasAnnotation(m, "InterpretEntry");
        String proc = procName(fqn, name, desc);
        Optional<Body> body = Optional.empty();
        boolean translated = false;
        List<Param> inputs;
        List<Param> outputs;
        boolean signature;
        try {
            inputs = paramsOf(fqn, isStatic, desc, range);
            outputs = outputsOf(desc, range);
            signature = true;
        } catch (Refusal r) {
            reportRefusal(r, range);
            inputs = List.of();
            outputs = List.of();
            signature = false;
        }
        if (signature) {
            try {
                String spec = specAnnotation(m);
                if (spec == null) {
                    spec = specAnnotation(cm);
                }
                if (spec != null && verify) {
                    throw new Refusal("not supported by the direct Cortex translation: @" + spec
                            + " (contracts and specifications)", range);
                }
                if (hasRangeAnnotation(m) || code != null && hasRangeAnnotation((CodeAttribute) code)) {
                    throw new Refusal("not supported by the direct Cortex translation: @Nat and @Unbounded",
                            range);
                }
                if (code != null && verify) {
                    body = Optional.of(new MethodTranslator(cm, fqn, m, (CodeAttribute) code, range, entry)
                            .translate());
                    translated = true;
                }
            } catch (Refusal r) {
                reportRefusal(r, range);
                body = Optional.empty();
                if (name.equals(CLINIT)) {
                    initializerRefusals.put(proc, r.getMessage());
                }
            }
        }
        procedures.put(proc, new Procedure(proc, inputs, outputs, throwsOf(cm, m), List.of(), body,
                entry && translated, range));
        uriOf.put(proc, src.uri);
        if (code == null) {
            return;
        }
        var status = translated
                ? MethodVerificationStatus.VerificationStatus.Verified
                : MethodVerificationStatus.VerificationStatus.Skipped;
        if (isBridge(m)) {
            return;
        }
        boolean implicitCtor = name.equals("<init>") && !hasCrt((CodeAttribute) code);
        var record = new MethodVerificationStatus(name,
                implicitCtor || mRange == SourceMap.UNKNOWN_RANGE ? Reporter.NO_RANGE : src.positionRange(mRange),
                status);
        statusOf.put(proc, record);
        recordStatus(implicitCtor ? SourceMap.UNKNOWN_RANGE : mRange, record);
    }

    /**
     * A method whose guard calls a {@code <clinit>} that has no body (its
     * translation was refused) is refused with that reason, as on the Laurel
     * route: the interpreter runs a call to a bodiless procedure as one that
     * leaves every global unchanged, which the initializer would not have.
     * The method keeps its signature only. Repeated until nothing changes: a
     * refused initializer may itself be one another method's guard calls.
     */
    private void refuseUsersOfUntranslatedInitializers() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var e : List.copyOf(procedures.entrySet())) {
                if (refuseIfGuardCallsUntranslatedInitializer(e.getKey(), e.getValue())) {
                    changed = true;
                }
            }
        }
    }

    private boolean refuseIfGuardCallsUntranslatedInitializer(String name, Procedure p) {
        if (p.body().isEmpty()) {
            return false;
        }
        String clinit = null;
        for (Block b : p.body().get().blocks()) {
            for (Cmd c : b.cmds()) {
                if (c instanceof Cmd.Call call && call.proc().endsWith("$" + CLINIT)
                        && procedures.containsKey(call.proc()) && procedures.get(call.proc()).body().isEmpty()) {
                    clinit = call.proc();
                }
            }
        }
        if (clinit == null) {
            return false;
        }
        String owner = clinit.substring(0, clinit.length() - 1 - CLINIT.length());
        String reason = initializerRefusals.get(clinit);
        String message = "the static initializer of '" + JavaNames.simpleName(owner)
                + "', which this method may run, could not be translated" + (reason == null ? "" : ": " + reason);
        URI uri = uriOf.get(name);
        if (uri != null) {
            reporter.reportError(uri, src.pointOf(laurelRange(p.source())), message);
        }
        if (name.endsWith("$" + CLINIT)) {
            initializerRefusals.put(name, message);
        }
        procedures.put(name, new Procedure(p.name(), p.inputs(), p.outputs(), p.throws_(), p.requires(),
                Optional.empty(), false, p.source()));
        var status = statusOf.get(name);
        if (status != null) {
            status.setVerificationStatus(MethodVerificationStatus.VerificationStatus.Skipped);
        }
        return true;
    }

    private void reportRefusal(Refusal r, FileRange range) {
        var at = r.at != null ? r.at : range;
        reporter.reportError(src.uri, src.pointOf(laurelRange(at)), r.getMessage());
    }

    private static final List<String> SPEC_ANNOTATIONS = List.of(
            "Transparent", "Contract", "EmptyContract", "InheritContract", "Invariant", "AsProperty", "Erased");

    private static @Nullable String specAnnotation(java.lang.classfile.AttributedElement el) {
        for (String a : SPEC_ANNOTATIONS) {
            if (Declarations.hasAnnotation(el, a)) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasRangeAnnotation(java.lang.classfile.AttributedElement el) {
        for (var ta : org.strata.java.bytecode.frontend.ClassfileQueries.typeAnnotationsOn(el)) {
            String anno = ClassDesc.ofDescriptor(ta.annotation().className().stringValue()).displayName();
            if (anno.equals("Nat") || anno.equals("Unbounded")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCrt(CodeAttribute code) {
        return code.attributes().stream().anyMatch(a ->
                a instanceof java.lang.classfile.attribute.CharacterRangeTableAttribute crt
                        && !crt.characterRangeTable().isEmpty());
    }

    private void recordStatus(org.strata.jverify.laurel.FileRange range, MethodVerificationStatus status) {
        if (src.uri == null) {
            return;
        }
        var tree = methodStatuses.computeIfAbsent(src.uri, k -> new IntervalTree<>());
        if (range == SourceMap.UNKNOWN_RANGE) {
            tree.insert(0, 0, status);
            return;
        }
        var pos = src.positionRange(range);
        tree.insert(pos.start().line(), pos.end().line(), status);
    }

    // --- One method body ---

    private final class MethodTranslator {
        private final ClassModel cm;
        private final String owner;
        private final MethodModel method;
        private final FileRange methodRange;
        private final ControlFlowGraph cfg;
        private final List<CrtRange> crt;
        private final @Nullable LineNumbers lines;
        private final int[] seqToBci;
        private final Map<String, Ty> declared = new LinkedHashMap<>();
        private final List<Block> blocks = new ArrayList<>();
        private final Map<Integer, List<String>> labelsOf = new HashMap<>();
        /** The handlers of the guards' initializer calls ({@link #guard}); tried before the method's own. */
        private final List<Handler> guardHandlers = new ArrayList<>();
        private final Map<Integer, List<Kind>> entryShape = new HashMap<>();
        private final Map<Integer, List<String>> entryTypes = new HashMap<>();
        private int fresh;

        private List<Kind> stack = new ArrayList<>();
        private List<String> stackTypes = new ArrayList<>();
        private String label;
        private List<Cmd> cmds;
        private int blockId;
        /** An entry procedure: it starts by initializing its own class. */
        private final boolean entry;
        /** The types whose initialization has started when this method runs: no guard for them. */
        private final Set<String> initializationStarted;

        MethodTranslator(ClassModel cm, String owner, MethodModel method, CodeAttribute code, FileRange methodRange,
                         boolean entry) {
            this.cm = cm;
            this.owner = owner;
            this.method = method;
            this.methodRange = methodRange;
            this.entry = entry;
            this.initializationStarted = ClassInitialization.initializationStarted(resolver, owner);
            this.cfg = MethodBodyFactory.buildCfg(cm, method);
            this.crt = MethodBodyFactory.crtRanges(code);
            this.lines = LineNumbers.of(code);
            this.seqToBci = MethodBodyFactory.bciTable(cfg.seq);
        }

        Body translate() {
            var work = new ArrayDeque<Integer>();
            String first = "b0";
            if (!cfg.blocks.isEmpty()) {
                entryShape.put(0, List.of());
                entryTypes.put(0, nulls(0));
                work.add(0);
                // The prologue precedes b0 in blocks of its own: b0 may be a loop
                // header (a leading `while` test is at offset 0), and the prologue
                // must run once, not on every iteration.
                blockId = -1;
                startBlock("prologue");
                prologue();
                if (!cmds.isEmpty() || !label.equals("prologue")) {
                    seal(new Transfer.Goto("b0", methodRange));
                    first = "prologue";
                }
            }
            for (Trap t : cfg.excs) {
                if (!entryShape.containsKey(t.handlerBlk())) {
                    entryShape.put(t.handlerBlk(), List.of(Kind.A));
                    var ty = new ArrayList<String>();
                    ty.add(t.type() == null ? "java.lang.Throwable" : classFqn(t.type()));
                    entryTypes.put(t.handlerBlk(), ty);
                    work.add(t.handlerBlk());
                }
            }
            var done = new java.util.HashSet<Integer>();
            while (!work.isEmpty()) {
                int id = work.poll();
                if (!done.add(id)) {
                    continue;
                }
                translateBlock(cfg.blocks.get(id));
                for (int s : cfg.blocks.get(id).succ) {
                    var shape = entryShape.get(s);
                    if (shape == null) {
                        entryShape.put(s, List.copyOf(stack));
                        entryTypes.put(s, mergeTypes(null, stackTypes));
                        work.add(s);
                    } else if (!shape.equals(stack)) {
                        throw new Refusal("inconsistent operand stack at a join", methodRange);
                    } else {
                        entryTypes.put(s, mergeTypes(entryTypes.get(s), stackTypes));
                    }
                }
            }
            var handlers = new ArrayList<Handler>(guardHandlers);
            for (Trap t : cfg.excs) {
                if (!done.contains(t.handlerBlk())) {
                    continue;
                }
                var covered = new ArrayList<String>();
                for (int b = t.startBlk(); b < t.endBlkId(); b++) {
                    covered.addAll(labelsOf.getOrDefault(b, List.of()));
                }
                if (covered.isEmpty()) {
                    continue;
                }
                Optional<String> catchType = Optional.empty();
                if (t.type() != null) {
                    String c = classFqn(t.type());
                    referenced.add(c);
                    catchType = Optional.of(c);
                }
                handlers.add(new Handler(covered, catchType, stackVar(0, Kind.A), "b" + t.handlerBlk(),
                        methodRange));
            }
            var copies = new ArrayList<Cmd>();
            boolean isStatic = method.flags().has(AccessFlag.STATIC);
            int slot = 0;
            int arg = 0;
            if (!isStatic) {
                copies.add(new Cmd.Set(localVar(0, Kind.A), Optional.of(new Expr.Var("arg0")), methodRange));
                slot = 1;
                arg = 1;
            }
            for (var p : method.methodTypeSymbol().parameterList()) {
                Kind k = kindOf(p, methodRange);
                copies.add(new Cmd.Set(localVar(slot, k), Optional.of(new Expr.Var("arg" + arg)), methodRange));
                slot += k == Kind.J ? 2 : 1;
                arg++;
            }
            var entryCmds = new ArrayList<Cmd>();
            for (var d : declared.entrySet()) {
                entryCmds.add(new Cmd.Init(d.getKey(), d.getValue(), defaultOf(d.getValue()), methodRange));
            }
            entryCmds.addAll(copies);
            var all = new ArrayList<Block>();
            all.add(new Block("entry", entryCmds, new Transfer.Goto(first, methodRange)));
            all.addAll(blocks);
            return new Body("entry", all, handlers);
        }

        private Optional<Expr> defaultOf(Ty ty) {
            return switch (ty) {
                case Ty.Bool b -> Optional.of(new Expr.Bool(false));
                case Ty.Bv bv -> Optional.of(new Expr.Bv(bv.width(), 0));
                default -> Optional.empty();
            };
        }

        private List<String> nulls(int n) {
            var l = new ArrayList<String>();
            for (int i = 0; i < n; i++) {
                l.add(null);
            }
            return l;
        }

        private List<String> mergeTypes(@Nullable List<String> a, List<String> b) {
            var out = new ArrayList<String>();
            for (int i = 0; i < b.size(); i++) {
                String x = b.get(i);
                out.add(a == null || java.util.Objects.equals(a.get(i), x) ? x : null);
            }
            return out;
        }

        // --- Names and declarations ---

        private String declare(String name, Ty ty) {
            declared.putIfAbsent(name, ty);
            return name;
        }

        private String stackVar(int depth, Kind k) {
            return declare("s" + depth + k, k.stackTy());
        }

        private String localVar(int slot, Kind k) {
            return declare("l" + slot + k, k.stackTy());
        }

        private String temp(Ty ty) {
            return declare("t" + (fresh++), ty);
        }

        // --- Blocks ---

        private void startBlock(String l) {
            label = l;
            cmds = new ArrayList<>();
            labelsOf.computeIfAbsent(blockId, k -> new ArrayList<>()).add(l);
        }

        private void seal(Transfer t) {
            blocks.add(new Block(label, cmds, t));
        }

        private String freshLabel() {
            return (blockId < 0 ? "prologue" : "b" + blockId) + "_" + (fresh++);
        }

        // --- Stack ---

        private Expr push(Kind k, Expr value, @Nullable String type, FileRange at) {
            String v = stackVar(stack.size(), k);
            cmds.add(new Cmd.Set(v, Optional.of(value), at));
            stack.add(k);
            stackTypes.add(type);
            return new Expr.Var(v);
        }

        private String pushTarget(Kind k, @Nullable String type) {
            String v = stackVar(stack.size(), k);
            stack.add(k);
            stackTypes.add(type);
            return v;
        }

        private record Value(Kind kind, Expr expr, @Nullable String type) {
        }

        private Value pop() {
            int d = stack.size() - 1;
            Kind k = stack.remove(d);
            String t = stackTypes.remove(d);
            return new Value(k, new Expr.Var("s" + d + k), t);
        }

        private Value snapshot(Value v, FileRange at) {
            String t = temp(v.kind.stackTy());
            cmds.add(new Cmd.Set(t, Optional.of(v.expr), at));
            return new Value(v.kind, new Expr.Var(t), v.type);
        }

        private void pushValues(List<Value> values, FileRange at) {
            for (var v : values) {
                push(v.kind, v.expr, v.type, at);
            }
        }

        private List<Value> popSnapshots(int n, FileRange at) {
            var vs = new ArrayList<Value>();
            for (int i = 0; i < n; i++) {
                vs.addFirst(snapshot(pop(), at));
            }
            return vs;
        }

        // --- Expressions ---

        private Expr op(String name, Expr... args) {
            return new Expr.Op(name, List.of(args));
        }

        private Expr lit(long v) {
            return new Expr.Bv(32, v);
        }

        private Expr lit(Kind k, long v) {
            return new Expr.Bv(k == Kind.J ? 64 : 32, v);
        }

        private Expr isZero(Expr e) {
            return new Expr.Eq(e, lit(0));
        }

        private Expr bv(Kind k, String op, Expr... args) {
            return op((k == Kind.J ? "Bv64." : "Bv32.") + op, args);
        }

        private Expr toInt(int width, Expr e) {
            return op("Bv" + width + ".ToInt", e);
        }

        private Expr toBv(int width, Expr e) {
            return op("Int.ToBv" + width, e);
        }

        /** A call to the prelude's {@code java$<name>}, its result pushed. */
        private void callPrelude(String name, Kind result, FileRange at, Expr... args) {
            cmds.add(new Cmd.Call(List.of(pushTarget(result, null)), PRELUDE + "$" + name, List.of(args), at));
        }

        private Expr not(Expr e) {
            return op("Bool.Not", e);
        }

        private Expr boolToInt(Expr b) {
            return new Expr.Ite(b, lit(1), lit(0));
        }

        // --- Ranges ---

        private FileRange rangeAt(int seqIdx) {
            Span s = MethodBodyFactory.spanOf(crt, seqToBci, seqIdx);
            if (!s.isNone()) {
                return cortexRange(src.rangeOf(s.beginLine(), s.beginCol(), s.endLine(), s.endCol()));
            }
            if (lines != null && seqIdx >= 0 && seqIdx < seqToBci.length) {
                int line = lines.lineAt(seqToBci[seqIdx]);
                if (line > 0) {
                    return cortexRange(src.lineRange(line));
                }
            }
            return methodRange;
        }

        private Refusal refuse(String what, FileRange at) {
            return new Refusal("not supported by the direct Cortex translation: " + what, at);
        }

        // --- Class initialization ---

        /**
         * The statements that start the body. An entry procedure runs with no
         * class initialized and initializes its own class before anything
         * else, as the JVM does before it runs a class's {@code main}.
         * {@code <clinit>} runs once, while its flag is still false, so its
         * class's static fields hold their defaults unless an initializer that
         * runs before it may have written them
         * ({@link ClassInitialization#staticsAtDefaultsInInitializer}): it
         * assigns the default to each field of a primitive type.
         */
        private void prologue() {
            if (entry) {
                guard(owner, Set.of(), methodRange);
            }
            if (method.methodName().stringValue().equals(CLINIT)
                    && ClassInitialization.staticsAtDefaultsInInitializer(resolver, owner)) {
                for (var f : cm.fields()) {
                    int flags = f.flags().flagsMask();
                    if ((flags & AccessFlag.STATIC.mask()) == 0 || (flags & ClassFile.ACC_SYNTHETIC) != 0) {
                        continue;
                    }
                    Ty ty = fieldTy(f.fieldTypeSymbol());
                    var defaultValue = ty == null ? Optional.<Expr>empty() : defaultOf(ty);
                    if (defaultValue.isEmpty()) {
                        continue;
                    }
                    String global = declareGlobal(owner, f.fieldName().stringValue(), f.fieldTypeSymbol(), methodRange);
                    // An assignment, not an assumption: the same fact, and it runs on the interpreter.
                    cmds.add(new Cmd.Set(global, defaultValue, methodRange));
                }
            }
        }

        /** Emit the guard for a use of class {@code fqn} here, if the use may run an initializer. */
        private void initialize(String fqn, FileRange at) {
            guard(fqn, initializationStarted, at);
        }

        /**
         * The guard for a use of {@code fqn}, as blocks: the current block ends
         * with the tests, and translation continues in the block the paths join in.
         * <pre>
         * if (failed) throw new NoClassDefFoundError();
         * if (!flag) {
         *   flag := true;
         *   try { <guards of the types initialized before>; } catch (Throwable t) { failed := true; throw t; }
         *   try { <clinit>(); } catch (Throwable t) {
         *     failed := true;
         *     throw t instanceof Error ? t : new ExceptionInInitializerError();
         *   }
         *   flag := true;
         * }
         * </pre>
         * The flag is set before the initializer runs, as the JVM marks a class
         * before running its initializer, so a class reached again during its
         * own initialization is not initialized twice; and again after the
         * call, which states a fact the JVM guarantees (nothing clears the
         * flag) where a caller that knows nothing about the callee's effect on
         * globals would otherwise lose it. An exception from the initializer
         * marks the class erroneous and reaches the caller wrapped, unless it
         * is an {@code Error} (JLS 12.4.2, step 11); one from a type
         * initialized before marks the class erroneous too and propagates as
         * it is (step 7). Every later use
         * throws {@code NoClassDefFoundError}. A type with nothing of its own
         * to run contributes only the guards of the types before it. An
         * initializer that is not verified ({@code @Verify(false)}) refuses
         * the method: the call would be to a procedure without a body.
         */
        private void guard(String fqn, Set<String> started, FileRange at) {
            if (!ClassInitialization.runsInitializer(resolver, fqn, started)) {
                return;
            }
            MethodModel clinit = ClassInitialization.initializerOf(resolver, fqn);
            if (clinit == null) {
                for (String before : ClassInitialization.initializedBefore(resolver, fqn)) {
                    guard(before, started, at);
                }
                return;
            }
            if (resolver.isOptedOut(programClasses.get(fqn), clinit)) {
                throw new Refusal(StaticFields.notVerified(fqn), at);
            }
            String flag = declareInitFlag(fqn, at);
            String failed = declareInitFailedFlag(fqn, at);
            throwIf(new Expr.Var(failed), NO_CLASS_DEF_FOUND_ERROR, at);
            String init = freshLabel();
            String cont = freshLabel();
            seal(new Transfer.CondGoto(new Expr.Var(flag), cont, init, at));
            startBlock(init);
            cmds.add(new Cmd.Set(flag, Optional.of(new Expr.Bool(true)), at));
            String call = freshLabel();
            var before = ClassInitialization.initializedBefore(resolver, fqn);
            if (!before.isEmpty()) {
                // The guards of the types initialized before, in blocks of their
                // own: if one of them throws, this class is erroneous too and the
                // exception propagates as it is (JLS 12.4.2, step 7).
                String supertypes = freshLabel();
                seal(new Transfer.Goto(supertypes, at));
                startBlock(supertypes);
                int mark = blocks.size();
                for (String b : before) {
                    guard(b, started, at);
                }
                seal(new Transfer.Goto(call, at));
                var covered = new ArrayList<String>();
                for (Block b : blocks.subList(mark, blocks.size())) {
                    covered.add(b.label());
                }
                referenced.add(THROWABLE);
                String caughtBefore = temp(new Ty.Named(THROWABLE));
                String failedBefore = freshLabel();
                guardHandlers.add(new Handler(covered, Optional.empty(), caughtBefore, failedBefore, at));
                startBlock(failedBefore);
                cmds.add(new Cmd.Set(failed, Optional.of(new Expr.Bool(true)), at));
                cmds.add(new Cmd.Throw(new Expr.Var(caughtBefore), Optional.of(uncaught(THROWABLE)), at));
                seal(new Transfer.Finish(at));
            } else {
                seal(new Transfer.Goto(call, at));
            }
            // The call sits in a block of its own, the only one its handler covers.
            startBlock(call);
            cmds.add(new Cmd.Call(List.of(), procName(fqn, CLINIT, CLINIT_DESC), List.of(), at));
            staticNotes.recordCall(src.uri, lineOf(at),
                    StaticEffectNotes.describe(StaticEffectNotes.Kind.INITIALIZER, JavaNames.simpleName(fqn)),
                    StaticEffectNotes.Kind.INITIALIZER);
            cmds.add(new Cmd.Set(flag, Optional.of(new Expr.Bool(true)), at));
            seal(new Transfer.Goto(cont, at));
            // The handler: mark the class erroneous, rethrow an Error, wrap anything else.
            referenced.add(THROWABLE);
            referenced.add(ERROR);
            referenced.add(EXCEPTION_IN_INITIALIZER_ERROR);
            String caught = temp(new Ty.Named(THROWABLE));
            String handler = freshLabel();
            guardHandlers.add(new Handler(List.of(call), Optional.empty(), caught, handler, at));
            startBlock(handler);
            cmds.add(new Cmd.Set(failed, Optional.of(new Expr.Bool(true)), at));
            String isError = temp(new Ty.Bool());
            cmds.add(new Cmd.InstanceOf(isError, new Expr.Var(caught), ERROR, at));
            String rethrow = freshLabel();
            String wrap = freshLabel();
            seal(new Transfer.CondGoto(new Expr.Var(isError), rethrow, wrap, at));
            startBlock(rethrow);
            cmds.add(new Cmd.Throw(new Expr.Var(caught), Optional.of(uncaught(ERROR)), at));
            seal(new Transfer.Finish(at));
            startBlock(wrap);
            String wrapped = temp(new Ty.Named(EXCEPTION_IN_INITIALIZER_ERROR));
            cmds.add(new Cmd.New(wrapped, EXCEPTION_IN_INITIALIZER_ERROR, at));
            cmds.add(new Cmd.Throw(new Expr.Var(wrapped), Optional.of(uncaught(EXCEPTION_IN_INITIALIZER_ERROR)), at));
            seal(new Transfer.Finish(at));
            startBlock(cont);
        }

        /** The read or write of a static field. */
        private void staticField(FieldInstruction fi, FileRange at) {
            String name = fi.name().stringValue();
            String target = JavaNames.dottedFqn(fi.owner().asInternalName());
            ClassDesc type = fi.typeSymbol();
            boolean read = fi.opcode() == Opcode.GETSTATIC;
            if (ClassInitialization.isAssertionsDisabled(resolver, fi)) {
                // Assertions are translated as always enabled: a read is false,
                // and the assignment in <clinit> is dropped.
                if (read) {
                    push(Kind.I, lit(0), null, at);
                } else {
                    pop();
                }
                return;
            }
            if (ClassInitialization.isEnumValues(resolver, fi)) {
                if (read) {
                    throw refuse("arrays", at);
                }
                pop();   // values() is not translated, so nothing reads the array
                return;
            }
            if (interpret && type.isArray()) {
                throw refuse("arrays in static fields", at);
            }
            String declaring = ClassInitialization.declaringClassOfStatic(resolver, target, name);
            if (declaring == null) {
                if (programClasses.containsKey(target)) {
                    throw refuse("fields of library classes", at);
                }
                if (!read) {
                    throw refuse("writes to static fields of library classes", at);
                }
                String global = declareLibraryGlobal(target, name, type, at);
                staticNotes.recordStaticRead(src.uri, lineOf(at));
                push(kindOf(type, at), new Expr.Var(global), type.isPrimitive() ? null : classFqn(type), at);
                return;
            }
            for (var f : programClasses.get(declaring).fields()) {
                if (f.fieldName().stringValue().equals(name) && hasRangeAnnotation(f)) {
                    throw refuse("@Nat and @Unbounded", at);
                }
            }
            if (fieldTy(type) == null) {
                throw refuse(type.isArray() ? "arrays" : "floating point", at);
            }
            if (read) {
                initialize(declaring, at);
                String global = declareGlobal(declaring, name, type, at);
                staticNotes.recordStaticRead(src.uri, lineOf(at));
                push(kindOf(type, at), new Expr.Var(global), type.isPrimitive() ? null : classFqn(type), at);
            } else {
                var value = pop();   // evaluated before the class is initialized, in a local the initializer cannot touch
                initialize(declaring, at);
                String global = declareGlobal(declaring, name, type, at);
                cmds.add(new Cmd.Set(global, Optional.of(value.expr), at));
            }
        }

        // --- Implicit throws ---

        private void throwIf(Expr cond, String exception, FileRange at) {
            String fail = freshLabel();
            String cont = freshLabel();
            seal(new Transfer.CondGoto(cond, fail, cont, at));
            startBlock(fail);
            referenced.add(exception);
            String e = temp(new Ty.Named(exception));
            cmds.add(new Cmd.New(e, exception, at));
            cmds.add(new Cmd.Throw(new Expr.Var(e), Optional.of(uncaught(exception)), at));
            seal(new Transfer.Finish(at));
            startBlock(cont);
        }

        private String uncaught(@Nullable String type) {
            return type == null ? "uncaught exception is not thrown" : "uncaught exception '" + type + "' is not thrown";
        }

        // --- One block ---

        private void translateBlock(BasicBlock b) {
            blockId = b.id;
            stack = new ArrayList<>(entryShape.get(b.id));
            stackTypes = new ArrayList<>(entryTypes.get(b.id));
            startBlock("b" + b.id);
            for (int idx : b.instrs) {
                CodeElement el = cfg.seq.get(idx);
                if (el instanceof Instruction in) {
                    instruction(in, idx, b);
                }
            }
            var last = b.last;
            boolean terminated = last instanceof BranchInstruction || last instanceof ReturnInstruction
                    || last instanceof ThrowInstruction || last instanceof TableSwitchInstruction
                    || last instanceof LookupSwitchInstruction;
            if (!terminated) {
                FileRange at = rangeAt(b.instrs.getLast());
                seal(b.fall >= 0 ? new Transfer.Goto("b" + b.fall, at) : new Transfer.Finish(at));
            }
        }

        private void instruction(Instruction in, int idx, BasicBlock b) {
            FileRange at = rangeAt(idx);
            switch (in) {
                case NopInstruction n -> { }
                case ConstantInstruction ci -> constant(ci, at);
                case LoadInstruction li -> {
                    Kind k = kindOf(li.typeKind(), at);
                    push(k, new Expr.Var(localVar(li.slot(), k)), null, at);
                }
                case StoreInstruction si -> {
                    Kind k = kindOf(si.typeKind(), at);
                    var v = pop();
                    cmds.add(new Cmd.Set(localVar(si.slot(), k), Optional.of(v.expr), at));
                }
                case IncrementInstruction ii -> {
                    String v = localVar(ii.slot(), Kind.I);
                    cmds.add(new Cmd.Call(List.of(v), PRELUDE + "$iadd", List.of(new Expr.Var(v), lit(ii.constant())), at));
                }
                case OperatorInstruction oi when interpret && oi.opcode() == Opcode.ARRAYLENGTH -> {
                    var array = pop();
                    String length = temp(new Ty.Int());
                    cmds.add(new Cmd.ArrayLength(length, array.expr, at));
                    push(Kind.I, toBv(32, new Expr.Var(length)), null, at);
                }
                case OperatorInstruction oi -> operator(oi, at);
                case ConvertInstruction cv -> convert(cv, at);
                case StackInstruction sk -> stackOp(sk, at);
                case NewObjectInstruction nw -> {
                    String c = JavaNames.dottedFqn(nw.className().asInternalName());
                    referenced.add(c);
                    initialize(c, at);
                    cmds.add(new Cmd.New(pushTarget(Kind.A, c), c, at));
                }
                case NewPrimitiveArrayInstruction na when interpret -> newArray(na, at);
                case NewReferenceArrayInstruction nr when interpret ->
                        throw refuse(nr.componentType().asSymbol().isArray()
                                ? "multi-dimensional arrays" : "arrays of references", at);
                case NewMultiArrayInstruction nm when interpret -> throw refuse("multi-dimensional arrays", at);
                case ArrayLoadInstruction al when interpret -> {
                    Ty stored = storedAs(al.typeKind(), at);
                    var index = pop();
                    var array = pop();
                    Expr i = checkedIndex(array, index, at);
                    cmds.add(new Cmd.ReadElem(pushTarget(kindOf(al.typeKind(), at), null), array.expr, i, stored, at));
                }
                case ArrayStoreInstruction as when interpret -> {
                    Ty stored = storedAs(as.typeKind(), at);
                    var value = pop();
                    var index = pop();
                    var array = pop();
                    Expr i = checkedIndex(array, index, at);
                    cmds.add(new Cmd.WriteElem(array.expr, i, narrowed(as.typeKind(), value.expr), stored, at));
                }
                case FieldInstruction fi -> field(fi, at);
                case InvokeInstruction inv -> invoke(inv, at);
                case TypeCheckInstruction tc -> typeCheck(tc, at);
                case BranchInstruction br -> branch(br, b, at);
                case TableSwitchInstruction sw -> switchOn(b, at);
                case LookupSwitchInstruction sw -> switchOn(b, at);
                case ReturnInstruction ri -> {
                    if (ri.typeKind() != TypeKind.VOID) {
                        var v = pop();
                        cmds.add(new Cmd.Set(RESULT, Optional.of(v.expr), at));
                    }
                    seal(new Transfer.Finish(at));
                }
                case ThrowInstruction t -> {
                    var v = pop();
                    cmds.add(new Cmd.Throw(v.expr, Optional.of(uncaught(v.type)), at));
                    seal(new Transfer.Finish(at));
                }
                default -> throw refuse("the instruction " + in.opcode().name().toLowerCase(), at);
            }
        }

        private void newArray(NewPrimitiveArrayInstruction na, FileRange at) {
            ClassDesc elem = switch (na.typeKind()) {
                case BOOLEAN -> ConstantDescs.CD_boolean;
                case BYTE -> ConstantDescs.CD_byte;
                case SHORT -> ConstantDescs.CD_short;
                case CHAR -> ConstantDescs.CD_char;
                case INT -> ConstantDescs.CD_int;
                case LONG -> ConstantDescs.CD_long;
                default -> throw refuse("floating point", at);
            };
            String type = arrayType(elem.arrayType(), at);
            var count = pop();
            throwIf(bv(Kind.I, "SLt", count.expr, lit(0)), "java.lang.NegativeArraySizeException", at);
            cmds.add(new Cmd.NewArray(pushTarget(Kind.A, null), type, toInt(32, count.expr), at));
        }

        /** The type the elements of the array that an instruction of kind {@code tk} accesses are stored as. */
        private Ty storedAs(TypeKind tk, FileRange at) {
            return switch (tk) {
                case BOOLEAN, BYTE, SHORT, CHAR, INT -> new Ty.Bv(32);
                case LONG -> new Ty.Bv(64);
                case REFERENCE -> throw refuse("arrays of references", at);
                default -> throw refuse("floating point", at);
            };
        }

        /** The index of {@code array[index]} as a mathematical integer, after the check that it is in range. */
        private Expr checkedIndex(Value array, Value index, FileRange at) {
            Expr i = toInt(32, index.expr);
            String length = temp(new Ty.Int());
            cmds.add(new Cmd.ArrayLength(length, array.expr, at));
            throwIf(op("Bool.Or", op("Int.Lt", i, new Expr.Int(0)), op("Int.Ge", i, new Expr.Var(length))),
                    "java.lang.ArrayIndexOutOfBoundsException", at);
            return i;
        }

        /**
         * The value a store of kind {@code tk} writes: {@code bastore}, {@code castore} and {@code sastore}
         * keep the low bits of the {@code int} on the stack, sign- or zero-extended.
         */
        private Expr narrowed(TypeKind tk, Expr value) {
            return switch (tk) {
                case BYTE -> toBv(32, op("Bv8.ToInt", toBv(8, toInt(32, value))));
                case SHORT -> toBv(32, op("Bv16.ToInt", toBv(16, toInt(32, value))));
                case CHAR -> toBv(32, op("Bv16.ToUInt", toBv(16, toInt(32, value))));
                default -> value;
            };
        }

        private void constant(ConstantInstruction ci, FileRange at) {
            Object v = ci.constantValue();
            switch (v) {
                case null -> throw refuse("null", at);
                case Integer i -> push(Kind.I, lit(i), null, at);
                case Long l -> push(Kind.J, lit(Kind.J, l), null, at);
                case String s -> {
                    referenced.add("java.lang.String");
                    String t = pushTarget(Kind.A, "java.lang.String");
                    cmds.add(new Cmd.Set(t, Optional.empty(), at));
                }
                case ClassDesc c -> throw refuse("class literals", at);
                default -> throw refuse(v instanceof Float || v instanceof Double
                        ? "floating point" : "the constant " + v, at);
            }
        }

        private void operator(OperatorInstruction oi, FileRange at) {
            Opcode o = oi.opcode();
            String name = o.name();
            if (name.startsWith("F") || name.startsWith("D")) {
                throw refuse("floating point", at);
            }
            Kind k = name.startsWith("L") ? Kind.J : Kind.I;
            String jvm = name.toLowerCase();
            switch (o) {
                case IADD, LADD, ISUB, LSUB, IMUL, LMUL, IDIV, LDIV, IREM, LREM -> {
                    var r = pop();
                    var l = pop();
                    if (o == Opcode.IDIV || o == Opcode.LDIV || o == Opcode.IREM || o == Opcode.LREM) {
                        referenced.add(ARITHMETIC_EXCEPTION);
                    }
                    callPrelude(jvm, k, at, l.expr, r.expr);
                }
                case INEG, LNEG -> {
                    var v = pop();
                    callPrelude(jvm, k, at, v.expr);
                }
                case IAND, LAND, IOR, LOR, IXOR, LXOR -> {
                    var r = pop();
                    var l = pop();
                    String bvOp = switch (o) {
                        case IAND, LAND -> "And";
                        case IOR, LOR -> "Or";
                        default -> "Xor";
                    };
                    push(k, bv(k, bvOp, l.expr, r.expr), null, at);
                }
                case ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR -> {
                    var amount = pop();
                    var v = pop();
                    String bvOp = switch (o) {
                        case ISHL, LSHL -> "Shl";
                        case ISHR, LSHR -> "SShr";
                        default -> "UShr";
                    };
                    Expr shift = k == Kind.J
                            ? bv(Kind.J, "And", toBv(64, toInt(32, amount.expr)), lit(Kind.J, 63))
                            : bv(Kind.I, "And", amount.expr, lit(31));
                    push(k, bv(k, bvOp, v.expr, shift), null, at);
                }
                case LCMP -> {
                    var r = pop();
                    var l = pop();
                    push(Kind.I, new Expr.Ite(bv(Kind.J, "SLt", l.expr, r.expr), lit(-1),
                            new Expr.Ite(new Expr.Eq(l.expr, r.expr), lit(0), lit(1))), null, at);
                }
                default -> throw refuse("the instruction " + jvm, at);
            }
        }

        private void convert(ConvertInstruction cv, FileRange at) {
            var o = cv.opcode();
            switch (o) {
                case I2L -> {
                    var v = pop();
                    push(Kind.J, toBv(64, toInt(32, v.expr)), null, at);
                }
                case L2I, I2B, I2S, I2C -> {
                    var v = pop();
                    callPrelude(o.name().toLowerCase(), Kind.I, at, v.expr);
                }
                default -> throw refuse("floating point", at);
            }
        }

        private boolean wide(int depthFromTop) {
            int i = stack.size() - 1 - depthFromTop;
            return i >= 0 && stack.get(i) == Kind.J;
        }

        private void stackOp(StackInstruction sk, FileRange at) {
            switch (sk.opcode()) {
                case POP -> pop();
                case POP2 -> {
                    if (wide(0)) {
                        pop();
                    } else {
                        pop();
                        pop();
                    }
                }
                case DUP -> {
                    var v = popSnapshots(1, at);
                    pushValues(v, at);
                    pushValues(v, at);
                }
                case SWAP -> {
                    var v = popSnapshots(2, at);
                    pushValues(List.of(v.get(1), v.get(0)), at);
                }
                case DUP_X1 -> {
                    var v = popSnapshots(2, at);
                    pushValues(List.of(v.get(1), v.get(0), v.get(1)), at);
                }
                case DUP_X2 -> {
                    if (wide(1)) {
                        var v = popSnapshots(2, at);
                        pushValues(List.of(v.get(1), v.get(0), v.get(1)), at);
                    } else {
                        var v = popSnapshots(3, at);
                        pushValues(List.of(v.get(2), v.get(0), v.get(1), v.get(2)), at);
                    }
                }
                case DUP2 -> {
                    if (wide(0)) {
                        var v = popSnapshots(1, at);
                        pushValues(v, at);
                        pushValues(v, at);
                    } else {
                        var v = popSnapshots(2, at);
                        pushValues(v, at);
                        pushValues(v, at);
                    }
                }
                case DUP2_X1 -> {
                    if (wide(0)) {
                        var v = popSnapshots(2, at);
                        pushValues(List.of(v.get(1), v.get(0), v.get(1)), at);
                    } else {
                        var v = popSnapshots(3, at);
                        pushValues(List.of(v.get(1), v.get(2), v.get(0), v.get(1), v.get(2)), at);
                    }
                }
                default -> throw refuse("the instruction " + sk.opcode().name().toLowerCase(), at);
            }
        }

        private void field(FieldInstruction fi, FileRange at) {
            if (fi.opcode() == Opcode.GETSTATIC || fi.opcode() == Opcode.PUTSTATIC) {
                staticField(fi, at);
                return;
            }
            String name = fi.name().stringValue();
            String declaredIn = fieldOwner(JavaNames.dottedFqn(fi.owner().asInternalName()), name);
            if (declaredIn == null) {
                throw refuse("fields of library classes", at);
            }
            ClassDesc type = fi.typeSymbol();
            for (var f : programClasses.get(declaredIn).fields()) {
                if (f.fieldName().stringValue().equals(name) && hasRangeAnnotation(f)) {
                    throw refuse("@Nat and @Unbounded", at);
                }
            }
            tyOf(type, at);
            if (fi.opcode() == Opcode.GETFIELD) {
                var obj = pop();
                String v = pushTarget(kindOf(type, at), type.isPrimitive() || type.isArray() ? null : classFqn(type));
                cmds.add(new Cmd.ReadField(v, obj.expr, declaredIn, name, at));
            } else {
                var value = pop();
                var obj = pop();
                cmds.add(new Cmd.WriteField(obj.expr, declaredIn, name, value.expr, at));
            }
        }

        private void typeCheck(TypeCheckInstruction tc, FileRange at) {
            ClassDesc t = tc.type().asSymbol();
            String c;
            if (t.isArray()) {
                c = arrayType(t, at);
            } else {
                c = classFqn(t);
                referenced.add(c);
            }
            if (tc.opcode() == Opcode.INSTANCEOF) {
                var v = pop();
                String b = temp(new Ty.Bool());
                cmds.add(new Cmd.InstanceOf(b, v.expr, c, at));
                push(Kind.I, boolToInt(new Expr.Var(b)), null, at);
            } else {
                int d = stack.size() - 1;
                String b = temp(new Ty.Bool());
                cmds.add(new Cmd.InstanceOf(b, new Expr.Var("s" + d + Kind.A), c, at));
                throwIf(not(new Expr.Var(b)), "java.lang.ClassCastException", at);
                stackTypes.set(d, t.isArray() ? null : c);
            }
        }

        private void branch(BranchInstruction br, BasicBlock b, FileRange at) {
            Opcode o = br.opcode();
            if (o == Opcode.GOTO || o == Opcode.GOTO_W) {
                seal(new Transfer.Goto("b" + b.gotoTarget, at));
                return;
            }
            Expr cond = switch (o) {
                case IFEQ -> isZero(pop().expr);
                case IFNE -> not(isZero(pop().expr));
                case IFLT -> bv(Kind.I, "SLt", pop().expr, lit(0));
                case IFGE -> bv(Kind.I, "SGe", pop().expr, lit(0));
                case IFGT -> bv(Kind.I, "SGt", pop().expr, lit(0));
                case IFLE -> bv(Kind.I, "SLe", pop().expr, lit(0));
                case IF_ICMPEQ, IF_ACMPEQ -> {
                    var r = pop();
                    yield new Expr.Eq(pop().expr, r.expr);
                }
                case IF_ICMPNE, IF_ACMPNE -> {
                    var r = pop();
                    yield not(new Expr.Eq(pop().expr, r.expr));
                }
                case IF_ICMPLT -> {
                    var r = pop();
                    yield bv(Kind.I, "SLt", pop().expr, r.expr);
                }
                case IF_ICMPGE -> {
                    var r = pop();
                    yield bv(Kind.I, "SGe", pop().expr, r.expr);
                }
                case IF_ICMPGT -> {
                    var r = pop();
                    yield bv(Kind.I, "SGt", pop().expr, r.expr);
                }
                case IF_ICMPLE -> {
                    var r = pop();
                    yield bv(Kind.I, "SLe", pop().expr, r.expr);
                }
                case IFNULL, IFNONNULL -> throw refuse("null", at);
                default -> throw refuse("the instruction " + o.name().toLowerCase(), at);
            };
            seal(new Transfer.CondGoto(cond, "b" + b.branch, "b" + b.fall, at));
        }

        private void switchOn(BasicBlock b, FileRange at) {
            var key = pop();
            for (int[] c : b.cases) {
                String next = freshLabel();
                seal(new Transfer.CondGoto(new Expr.Eq(key.expr, lit(c[0])), "b" + c[1], next, at));
                startBlock(next);
            }
            seal(new Transfer.Goto("b" + b.defaultCase, at));
        }

        private void invoke(InvokeInstruction inv, FileRange at) {
            String target = JavaNames.dottedFqn(inv.owner().asInternalName());
            String name = inv.name().stringValue();
            MethodTypeDesc desc = inv.typeSymbol();
            Opcode o = inv.opcode();
            if (target.equals(JVERIFY)) {
                spec(name, desc, at);
                return;
            }
            if (interpret && inv.owner().asSymbol().isArray()) {
                throw refuse("the array method " + name, at);
            }
            boolean isStatic = o == Opcode.INVOKESTATIC;
            if (ClassInitialization.isEnumValuesMethod(resolver, inv)) {
                // Only in an enum's <clinit>, as the value assigned to $VALUES, an
                // assignment that is dropped: any reference does.
                pushTarget(Kind.A, null);
                return;
            }
            var args = new ArrayList<Expr>();
            for (int i = 0; i < desc.parameterCount(); i++) {
                args.addFirst(pop().expr);
            }
            if (!isStatic) {
                args.addFirst(pop().expr);
            }
            String declaringIn = declaringClass(target, name, desc, isStatic);
            if (isStatic && declaringIn != null) {
                // A call to a static method is a first use of the class that
                // declares it (JLS 12.4.1), not of the class the instruction
                // names. The arguments are evaluated before, in locals the
                // initializer cannot touch.
                initialize(declaringIn, at);
            }
            if (name.equals("<init>") && !programClasses.containsKey(target)
                    && (target.equals(OBJECT) || target.equals("java.lang.Enum") || resolver.isThrowableSubtype(target))) {
                // The name and ordinal an enum constructor passes to Enum are not modelled.
                return;
            }
            var lhs = new ArrayList<String>();
            if (!desc.returnType().descriptorString().equals("V")) {
                var rt = desc.returnType();
                lhs.add(pushTarget(kindOf(rt, at), rt.isPrimitive() ? null : rt.isArray() ? null : classFqn(rt)));
            }
            if (declaringIn == null && interpret) {
                throw refuse("calls to library methods (" + target + "." + name + ") when interpreting", at);
            }
            MethodModel callee = declaringIn == null ? null : declared(programClasses.get(declaringIn), name, desc, isStatic);
            boolean isPrivate = callee != null && callee.flags().has(AccessFlag.PRIVATE);
            var kind = declaringIn == null ? StaticEffectNotes.Kind.NO_BODY : StaticEffectNotes.Kind.BODY;
            staticNotes.recordCall(src.uri, lineOf(at), StaticEffectNotes.describe(kind, name + "()"), kind);
            if ((o == Opcode.INVOKEVIRTUAL || o == Opcode.INVOKEINTERFACE) && !isPrivate) {
                String ownerClass = declaringIn != null ? declaringIn : target;
                if (declaringIn == null) {
                    libraryProc(target, name, desc, false, at);
                }
                cmds.add(new Cmd.VirtualCall(lhs, ownerClass, methodKey(name, desc), args, at));
                return;
            }
            String proc = declaringIn != null ? procName(declaringIn, name, desc)
                    : libraryProc(target, name, desc, isStatic, at);
            cmds.add(new Cmd.Call(lhs, proc, args, at));
        }

        private void spec(String name, MethodTypeDesc desc, FileRange at) {
            String d = desc.descriptorString();
            switch (name + d) {
                case "check(Z)V" -> cmds.add(new Cmd.Assert("assert_" + (fresh++), not(isZero(pop().expr)),
                        Optional.empty(), at));
                case "assume(Z)V" -> cmds.add(new Cmd.Assume("assume_" + (fresh++), not(isZero(pop().expr)), at));
                case "implies(ZZ)Z" -> {
                    var r = pop();
                    var l = pop();
                    push(Kind.I, boolToInt(op("Bool.Or", isZero(l.expr), not(isZero(r.expr)))), null, at);
                }
                default -> throw refuse("JVerify." + name + " (contracts and specifications)", at);
            }
        }
    }
}
