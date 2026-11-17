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
import org.strata.jverify.cortex.Block;
import org.strata.jverify.cortex.Body;
import org.strata.jverify.cortex.Cmd;
import org.strata.jverify.cortex.Composite;
import org.strata.jverify.cortex.Constrained;
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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
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
 * object of the Cortex array type of its element type, a subtype of
 * {@code java.lang.Object}, {@code java.lang.Cloneable} and
 * {@code java.io.Serializable}, and the array instructions are Cortex's array
 * commands. An {@code int} element is a {@code bv32} and a {@code long}
 * element a {@code bv64}. A {@code boolean}, {@code byte}, {@code char} or
 * {@code short} element is stored as the {@code int} the JVM computes with,
 * and its type is a constrained type over {@code bv32} that is named after
 * the Java type, so the five array types of {@code int}-like elements stay
 * different types. {@code baload} and {@code bastore} access both
 * {@code boolean[]} and {@code byte[]}; the static type of the array, which
 * the translation tracks for stack values and locals, tells which. Cortex
 * indexes an array with mathematical integers and leaves a negative length
 * and an index out of range undefined, so a check that throws
 * {@code NegativeArraySizeException} or
 * {@code ArrayIndexOutOfBoundsException} precedes each of them.
 *
 * <p>A string constant is an object of the composite {@code java.lang.String},
 * which has one field, {@code $value}, holding the text. The object of each
 * constant lives in a global, {@code $string$lit<n>}, that the procedure
 * {@code $string$init} allocates once, under a guard in the prologue of every
 * method that uses a string constant; so equal constants are one object in
 * every method, as in the JVM's string pool (JLS 3.10.5), and {@code ==} on
 * them is reference equality. {@code String.length()},
 * {@code String.equals(Object)} and the constructors {@code String()} and
 * {@code String(String)} are modelled inline on that field.
 * String concatenation ({@code invokedynamic} on {@code StringConcatFactory})
 * joins the operands' texts with {@code Str.Concat} into a new String object;
 * a String operand, constructor argument or {@code intern} receiver that may
 * be null is refused. {@code String.intern()} is
 * the procedure {@code $string$intern} over a pool, a linked list of
 * {@code $string$pool} nodes; when a program interns, each constant joins the
 * pool at its first {@code ldc}, so a string interned earlier with the same
 * text is what the constant resolves to (JVMS 5.1). Every other {@code String}
 * method is a library call.
 *
 * <p>A few other library classes are modelled the same way, with explicit
 * fields on their composite and inline models of their methods
 * ({@link MethodTranslator#libraryMethod}): {@code Integer} boxes
 * ({@code valueOf}, {@code intValue}; reference equality between values
 * that may be boxes is refused, as Java's box cache decides it),
 * {@code StringBuilder} ({@code append}, {@code toString},
 * {@code setLength(0)}), a {@code Throwable}'s message and cause
 * ({@code getMessage}, {@code getCause}, the constructors that set them;
 * {@code addSuppressed} records nothing), an enum constant's {@code name()}
 * and {@code ordinal()}, and records, whose generated {@code toString},
 * {@code hashCode} and {@code equals} are refused when called. These inline
 * models are temporary. They put knowledge of the Java language and the JDK
 * (what boxing does, what an exception's constructor stores) into a
 * translation of bytecode, where a call to a library method is just a call.
 * The long-term solution, planned next, describes library methods outside
 * this translation: a contract per method (preconditions, and either
 * postconditions or the library's own bytecode for the behaviour), so that
 * every library call is translated the same way and modular verification
 * has the specifications it needs. The models here exist only so that the
 * Cortex interpreter, which has no contract mechanism yet, can run programs
 * that reach these classes.
 *
 * <p>What the translation does not support it refuses, per method, with an
 * error: JVerify specifications (every {@code JVerify} call but {@code check},
 * {@code assume} and {@code implies}, and the specification annotations such
 * as {@code @Transparent}), {@code @Nat} and {@code @Unbounded}, {@code null}
 * literals and null tests, floating point, fields of library classes, class
 * literals, {@code invokedynamic} other than string concatenation (and, in a
 * concatenation, operands that are neither String nor boolean) and monitors;
 * arrays of references,
 * multi-dimensional arrays, arrays in static fields of library classes, a
 * {@code baload} or {@code bastore} on an array whose static type is not
 * known and, when verifying, every array; and, when interpreting, calls to library
 * methods, {@code clone} on an array included.
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
 *
 * <p>A static field of a class type or, when interpreting, of an array type
 * is a global without an initial value: the translation has no {@code null},
 * and no {@code <clinit>} resets the field. Until the program assigns it, it
 * refers to no object, and the interpreter stops, without a verdict, at the
 * instruction that needs the object. Nothing assigns an array in a static
 * field of a library class, so a read of one is refused when interpreting.
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
    /** The direct supertypes of an array of primitives (JLS 4.10.3), which every array type is a subtype of. */
    static final List<String> ARRAY_PARENTS = List.of(OBJECT, "java.lang.Cloneable", "java.io.Serializable");
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
    public static final String STRING = "java.lang.String";
    /** The field of the synthesized {@code java.lang.String} composite that holds the text. */
    public static final String STRING_VALUE = "$value";
    /** The global that records that {@link #STRING_INIT} ran; a {@code $} prefix occurs in no static field's global. */
    static final String STRING_READY = "$string$ready";
    /** The procedure that allocates the object of every string constant of the program. */
    public static final String STRING_INIT = "$string$init";
    /** The global holding the object of each string constant, by its text, in first-use order. */
    private final Map<String, String> stringLiterals = new LinkedHashMap<>();
    /**
     * The composite of a node of the string pool (JLS 3.10.5), a singly linked
     * list: the String it holds, the next node, and whether there is one, since
     * this translation has no null. The list starts with a sentinel node.
     */
    public static final String STRING_POOL = "$string$pool";
    static final String POOL_STR = "str";
    static final String POOL_NEXT = "next";
    static final String POOL_HAS_NEXT = "hasNext";
    /** The globals holding the pool's sentinel head and its last node. */
    public static final String STRING_POOL_HEAD = "$string$poolHead";
    public static final String STRING_POOL_TAIL = "$string$poolTail";
    public static final String MAYBE_NULL_STRING = "a String that may be null (a field, a call result or a parameter)"
            + " as a concatenation or StringBuilder.append operand, String constructor argument or intern() receiver";
    /** The procedure that implements {@code String.intern} on the pool. */
    public static final String STRING_INTERN = "$string$intern";
    /** Whether the program calls {@code String.intern}, so the pool is built. */
    private boolean internUsed;
    /** Whether the program boxes an int ({@code Integer.valueOf}), known before any method is translated. */
    private boolean boxes;
    static final String INIT_FLAG = "<initialized>";
    static final String INIT_FAILED_FLAG = "<initFailed>";
    static final String THROWABLE = "java.lang.Throwable";
    /** {@code Throwable}'s modelled state, written by its constructors: the detail message and the cause ({@link #libraryFields}). */
    static final String THROWABLE_MESSAGE = "$message";
    static final String THROWABLE_CAUSE = "$cause";
    static final String INTEGER = "java.lang.Integer";
    static final String STRING_BUILDER = "java.lang.StringBuilder";
    static final String ENUM = "java.lang.Enum";
    static final String RECORD = "java.lang.Record";
    /** {@code Enum}'s modelled state, written by its constructor: the constant's name and ordinal. */
    static final String ENUM_NAME = "$name";
    static final String ENUM_ORDINAL = "$ordinal";
    /**
     * The reference types a value of which may be an {@link #INTEGER} box: Integer and its
     * supertypes. Reference equality between such values is refused in a program that boxes
     * ({@link MethodTranslator#refuseBoxIdentity}). Temporary, with the box model: a model of
     * the Integer cache (JLS 5.1.7) or a contract for {@code Integer.valueOf} makes the
     * comparison exact and this set unnecessary.
     */
    static final Set<String> MAY_HOLD_BOX = Set.of(INTEGER, "java.lang.Number", OBJECT, "java.lang.Comparable",
            "java.io.Serializable", "java.lang.constant.Constable", "java.lang.constant.ConstantDesc");
    static final String ERROR = "java.lang.Error";
    static final String EXCEPTION_IN_INITIALIZER_ERROR = "java.lang.ExceptionInInitializerError";
    static final String NO_CLASS_DEF_FOUND_ERROR = "java.lang.NoClassDefFoundError";
    public static final String CLINIT = "<clinit>";
    static final MethodTypeDesc CLINIT_DESC = MethodTypeDesc.of(ClassDesc.ofDescriptor("V"));
    /** Whether the program names an array type, so that it declares {@link #ARRAY_PARENTS}. */
    private boolean usesArrays;
    /** The binder of the constraint of each of {@link #elementTypes}. */
    static final String ELEMENT_BINDER = "$v";
    /** The constrained types that are element types of the program's array types, by name, in first-use order. */
    private final Map<String, Constrained> elementTypes = new LinkedHashMap<>();

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
        for (var cm : programClasses.values()) {
            for (var m : cm.methods()) {
                var code = m.code();
                if (code.isPresent() && code.get().elementList().stream()
                        .anyMatch(el -> el instanceof InvokeInstruction inv && isStringIntern(inv))) {
                    // Known before any `ldc` is translated: a program that interns resolves its literals lazily.
                    declarePool(NO_LOCATION);
                }
                if (code.isPresent() && code.get().elementList().stream()
                        .anyMatch(el -> el instanceof InvokeInstruction inv && isBoxing(inv))) {
                    boxes = true;
                }
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
        if (!stringLiterals.isEmpty() || internUsed) {
            procedures.put(STRING_INIT, stringInitProcedure());
        }
        if (internUsed) {
            procedures.put(STRING_INTERN, stringInternProcedure());
        }
        var composites = composites();
        // Java arrays are covariant (JLS 4.10.3). The array parents are composites of the program
        // only when it names an array type.
        var program = new Program(composites, List.copyOf(elementTypes.values()), List.copyOf(procedures.values()),
                List.copyOf(globals.values()), List.of(PRELUDE), true, usesArrays ? ARRAY_PARENTS : List.of());
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
            if (c.equals(STRING_POOL)) {
                // Frontend-private, not a Java object: no parent.
                out.put(c, new Composite(c, List.of(), List.of(
                        new Field(POOL_STR, new Ty.Named(STRING)),
                        new Field(POOL_NEXT, new Ty.Named(STRING_POOL)),
                        new Field(POOL_HAS_NEXT, new Ty.Bool()))));
                work.add(STRING);
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
            var fields = libraryFields(c);
            out.put(c, new Composite(c, parents, fields));
            work.addAll(parents);
            for (var f : fields) {
                if (f.ty() instanceof Ty.Named n) {
                    work.add(n.name());
                }
            }
            for (String r : referenced) {
                if (!out.containsKey(r)) {
                    work.add(r);
                }
            }
        }
        return List.copyOf(out.values());
    }

    /**
     * The fields of a composite: the modelled state of a library class whose
     * methods the translation inlines (String, Integer and StringBuilder hold
     * their value; Throwable its message and cause; Enum the constant's name
     * and ordinal; see {@link MethodTranslator#libraryMethod}), or a program
     * class's instance fields. Any other library class has none. The modelled
     * fields are a temporary stand-in for the library classes' own state,
     * until library methods are described by contracts or translated from
     * their own bytecode (see the class comment).
     */
    private List<Field> libraryFields(String c) {
        return switch (c) {
            case STRING, STRING_BUILDER -> List.of(new Field(STRING_VALUE, new Ty.String_()));
            case INTEGER -> List.of(new Field(STRING_VALUE, new Ty.Bv(32)));
            case THROWABLE -> List.of(new Field(THROWABLE_MESSAGE, new Ty.Named(STRING)),
                    new Field(THROWABLE_CAUSE, new Ty.Named(THROWABLE)));
            case ENUM -> List.of(new Field(ENUM_NAME, new Ty.Named(STRING)), new Field(ENUM_ORDINAL, new Ty.Bv(32)));
            default -> fieldsOf(c);
        };
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
            return arrayType(cd, at);
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
     * The Cortex array type of a Java array type, which is the array type of its element type
     * ({@link #elementTy}). Its parents are {@link #ARRAY_PARENTS}, which are referenced so that
     * each is declared as a composite.
     */
    private Ty.Array arrayType(ClassDesc array, @Nullable FileRange at) {
        if (!interpret) {
            throw new Refusal("not supported by the direct Cortex translation: arrays", at);
        }
        ClassDesc elem = array.componentType();
        if (!elem.isPrimitive()) {
            throw new Refusal("not supported by the direct Cortex translation: "
                    + (elem.isArray() ? "multi-dimensional arrays" : "arrays of references"), at);
        }
        Ty stored = elementTy(elem.descriptorString(), at);
        referenced.addAll(ARRAY_PARENTS);
        usesArrays = true;
        return new Ty.Array(stored);
    }

    /**
     * The element type of the Cortex array type of a Java array whose elements have the primitive
     * type {@code descriptor}. An {@code int} or {@code long} element is a bit-vector. A
     * {@code boolean}, {@code byte}, {@code short} or {@code char} element is stored as the
     * {@code int} the JVM computes with, as a field is, and its type is a constrained type over
     * {@code bv32} that is named after the Java type and holds the {@code int}s of its values. An
     * array type is identified by its element type, so {@code boolean[]}, {@code byte[]},
     * {@code short[]}, {@code char[]} and {@code int[]} are five Cortex types.
     */
    private Ty elementTy(String descriptor, @Nullable FileRange at) {
        Expr v = new Expr.Var(ELEMENT_BINDER);
        return switch (descriptor) {
            case "Z" -> constrainedElement("boolean", unsignedAtMost(v, 1));
            case "B" -> constrainedElement("byte", signedBetween(v, Byte.MIN_VALUE, Byte.MAX_VALUE));
            case "S" -> constrainedElement("short", signedBetween(v, Short.MIN_VALUE, Short.MAX_VALUE));
            case "C" -> constrainedElement("char", unsignedAtMost(v, Character.MAX_VALUE));
            case "I" -> new Ty.Bv(32);
            case "J" -> new Ty.Bv(64);
            default -> throw new Refusal("not supported by the direct Cortex translation: floating point", at);
        };
    }

    /** The constrained type {@code name} over {@code bv32}, declared on first use; its witness is zero. */
    private Ty constrainedElement(String name, Expr constraint) {
        elementTypes.computeIfAbsent(name, n -> new Constrained(n, new Ty.Bv(32), ELEMENT_BINDER, constraint,
                Optional.of(new Expr.Bv(32, 0))));
        return new Ty.Named(name);
    }

    private static Expr unsignedAtMost(Expr v, long max) {
        return new Expr.Op("Bv32.ULe", List.of(v, new Expr.Bv(32, max)));
    }

    private static Expr signedBetween(Expr v, long min, long max) {
        return new Expr.Op("Bool.And", List.of(new Expr.Op("Bv32.SLe", List.of(new Expr.Bv(32, min), v)),
                new Expr.Op("Bv32.SLe", List.of(v, new Expr.Bv(32, max)))));
    }

    private static String classFqn(ClassDesc cd) {
        String d = cd.descriptorString();
        return JavaNames.dottedFqn(d.substring(1, d.length() - 1));
    }

    /**
     * The static type that a stack value or local of the Java type {@code t} is tracked with: a
     * class by name, an array type by descriptor, a primitive type not at all.
     */
    private static @Nullable String staticType(ClassDesc t) {
        return t.isPrimitive() ? null : t.isArray() ? t.descriptorString() : classFqn(t);
    }

    private static boolean isArrayType(@Nullable String staticType) {
        return staticType != null && staticType.startsWith("[");
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

    /**
     * Whether {@code m} is the {@code toString}, {@code hashCode} or {@code equals} javac
     * generates for a record (JLS 8.10.3): a call to the {@code ObjectMethods} bootstrap, which
     * the translation does not model. One the record declares itself has a body of its own.
     */
    private static boolean isGeneratedRecordMember(ClassModel cm, MethodModel m) {
        String name = m.methodName().stringValue();
        if (!(name.equals("toString") || name.equals("hashCode") || name.equals("equals"))
                || cm.superclass().map(c -> !c.asInternalName().equals("java/lang/Record")).orElse(true)) {
            return false;
        }
        return m.code().map(code -> code.elementList().stream().anyMatch(el -> el instanceof InvokeDynamicInstruction indy
                && indy.invokedynamic().bootstrap().bootstrapMethod().reference().owner().asInternalName()
                        .equals("java/lang/runtime/ObjectMethods"))).orElse(false);
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
            if (isGeneratedRecordMember(cm, m)) {
                continue;   // javac's record members, an ObjectMethods bootstrap; a call to one is refused
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

    /**
     * The global that holds the object of string constant {@code text},
     * declared on first use. Every {@code ldc} of the same text reads the
     * same global, so equal constants are one object in every method, as the
     * JVM's string pool makes them (JLS 3.10.5). The global has no
     * initializer: {@link #STRING_INIT} allocates the object, because an
     * initializer is an expression and cannot allocate.
     */
    private String stringLiteral(String text, FileRange at) {
        return stringLiterals.computeIfAbsent(text, t -> {
            referenced.add(STRING);
            String global = "$string$lit" + stringLiterals.size();
            globals.put(global, new Global(global, new Ty.Named(STRING), Optional.empty(), at));
            return global;
        });
    }

    /**
     * The procedure that allocates one {@code java.lang.String} object per
     * string constant of the program and writes its text to {@link #STRING_VALUE}.
     * It runs once, under the {@link #STRING_READY} guard of the first method
     * that uses a string constant or {@code intern}. When the program calls
     * {@code intern}, it also creates the pool, empty but for its sentinel
     * head: a constant joins the pool when its first {@code ldc} runs
     * ({@link MethodTranslator#constant}), as in the JVM (JVMS 5.1).
     */
    private Procedure stringInitProcedure() {
        var cmds = new ArrayList<Cmd>();
        if (internUsed) {
            cmds.add(new Cmd.Init("head", new Ty.Named(STRING_POOL), Optional.empty(), NO_LOCATION));
            cmds.add(new Cmd.New("head", STRING_POOL, NO_LOCATION));
            cmds.add(new Cmd.WriteField(new Expr.Var("head"), STRING_POOL, POOL_HAS_NEXT, new Expr.Bool(false), NO_LOCATION));
            cmds.add(new Cmd.Set(STRING_POOL_HEAD, Optional.of(new Expr.Var("head")), NO_LOCATION));
            cmds.add(new Cmd.Set(STRING_POOL_TAIL, Optional.of(new Expr.Var("head")), NO_LOCATION));
        }
        int i = 0;
        for (var e : stringLiterals.entrySet()) {
            String t = "t" + i;
            cmds.add(new Cmd.Init(t, new Ty.Named(STRING), Optional.empty(), NO_LOCATION));
            cmds.add(new Cmd.New(t, STRING, NO_LOCATION));
            cmds.add(new Cmd.WriteField(new Expr.Var(t), STRING, STRING_VALUE, new Expr.Str(e.getKey()), NO_LOCATION));
            cmds.add(new Cmd.Set(e.getValue(), Optional.of(new Expr.Var(t)), NO_LOCATION));
            i++;
        }
        var body = new Body("entry", List.of(new Block("entry", cmds, new Transfer.Finish(NO_LOCATION))), List.of());
        return new Procedure(STRING_INIT, List.of(), List.of(), Optional.empty(), List.of(), Optional.of(body),
                false, NO_LOCATION);
    }

    /** The commands that append a node {@code n} (a declared local) holding {@code s} after the pool's last node. */
    private static void appendToPool(List<Cmd> cmds, String n, Expr s) {
        var tail = new Expr.Var(STRING_POOL_TAIL);
        cmds.add(new Cmd.New(n, STRING_POOL, NO_LOCATION));
        cmds.add(new Cmd.WriteField(new Expr.Var(n), STRING_POOL, POOL_STR, s, NO_LOCATION));
        cmds.add(new Cmd.WriteField(new Expr.Var(n), STRING_POOL, POOL_HAS_NEXT, new Expr.Bool(false), NO_LOCATION));
        cmds.add(new Cmd.WriteField(tail, STRING_POOL, POOL_NEXT, new Expr.Var(n), NO_LOCATION));
        cmds.add(new Cmd.WriteField(tail, STRING_POOL, POOL_HAS_NEXT, new Expr.Bool(true), NO_LOCATION));
        cmds.add(new Cmd.Set(STRING_POOL_TAIL, Optional.of(new Expr.Var(n)), NO_LOCATION));
    }

    /** {@code Integer.valueOf(int)}: javac's boxing conversion, and the explicit call. */
    private static boolean isBoxing(InvokeInstruction inv) {
        return JavaNames.dottedFqn(inv.owner().asInternalName()).equals(INTEGER)
                && inv.name().stringValue().equals("valueOf")
                && inv.typeSymbol().descriptorString().equals("(I)Ljava/lang/Integer;");
    }

    private static boolean isStringIntern(InvokeInstruction inv) {
        return JavaNames.dottedFqn(inv.owner().asInternalName()).equals(STRING)
                && inv.name().stringValue().equals("intern")
                && inv.typeSymbol().descriptorString().equals("()Ljava/lang/String;");
    }

    /** Declares the pool's globals and composite, on the first {@code intern}. */
    private void declarePool(FileRange at) {
        internUsed = true;
        referenced.add(STRING_POOL);
        referenced.add(STRING);
        for (String g : List.of(STRING_POOL_HEAD, STRING_POOL_TAIL)) {
            globals.computeIfAbsent(g, n -> new Global(n, new Ty.Named(STRING_POOL), Optional.empty(), at));
        }
    }

    /**
     * {@code $string$intern(arg0) returns result}, {@code String.intern} as
     * specified: walk the pool; the first String with {@code arg0}'s text is the
     * result; when there is none, {@code arg0} is appended and is the result.
     * The pool grows only here, and a constant enters it when its first
     * {@code ldc} runs, through this procedure too. So a text has one
     * canonical object, and which object it is follows the order of events as
     * in the JVM (JVMS 5.1): a string interned before the first {@code ldc}
     * of a constant with its text is what that constant resolves to.
     */
    private Procedure stringInternProcedure() {
        var string = new Ty.Named(STRING);
        var node = new Ty.Named(STRING_POOL);
        var arg = new Expr.Var("arg0");
        var entry = new ArrayList<Cmd>();
        entry.add(new Cmd.Init("v", new Ty.String_(), Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.Init("cv", new Ty.String_(), Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.Init("cur", node, Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.Init("n", node, Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.Init("c", string, Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.Init("more", new Ty.Bool(), Optional.empty(), NO_LOCATION));
        entry.add(new Cmd.ReadField("v", arg, STRING, STRING_VALUE, NO_LOCATION));
        entry.add(new Cmd.Set("cur", Optional.of(new Expr.Var(STRING_POOL_HEAD)), NO_LOCATION));
        var loop = List.<Cmd>of(new Cmd.ReadField("more", new Expr.Var("cur"), STRING_POOL, POOL_HAS_NEXT, NO_LOCATION));
        var step = List.<Cmd>of(
                new Cmd.ReadField("cur", new Expr.Var("cur"), STRING_POOL, POOL_NEXT, NO_LOCATION),
                new Cmd.ReadField("c", new Expr.Var("cur"), STRING_POOL, POOL_STR, NO_LOCATION),
                new Cmd.ReadField("cv", new Expr.Var("c"), STRING, STRING_VALUE, NO_LOCATION));
        var found = List.<Cmd>of(new Cmd.Set(RESULT, Optional.of(new Expr.Var("c")), NO_LOCATION));
        var append = new ArrayList<Cmd>();
        appendToPool(append, "n", arg);
        append.add(new Cmd.Set(RESULT, Optional.of(arg), NO_LOCATION));
        var body = new Body("entry", List.of(
                new Block("entry", entry, new Transfer.Goto("loop", NO_LOCATION)),
                new Block("loop", loop, new Transfer.CondGoto(new Expr.Var("more"), "step", "append", NO_LOCATION)),
                new Block("step", step, new Transfer.CondGoto(new Expr.Eq(new Expr.Var("cv"), new Expr.Var("v")),
                        "found", "loop", NO_LOCATION)),
                new Block("found", found, new Transfer.Finish(NO_LOCATION)),
                new Block("append", append, new Transfer.Finish(NO_LOCATION))), List.of());
        return new Procedure(STRING_INTERN, List.of(new Param("arg0", string)), List.of(new Param(RESULT, string)),
                Optional.empty(), List.of(), Optional.of(body), false, NO_LOCATION);
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
        /** The stack-map frame at each bytecode offset that has one. */
        private final Map<Integer, StackMapFrameInfo> frames = new HashMap<>();
        private final Set<Integer> handlerBlocks = new java.util.HashSet<>();
        /** {@link #localTypes} at the end of each translated block. */
        private final Map<Integer, Map<Integer, String>> exitTypes = new HashMap<>();
        private int fresh;

        private List<Kind> stack = new ArrayList<>();
        /** The static type ({@link #staticType}) of each stack value, or null where it is not known. */
        private List<String> stackTypes = new ArrayList<>();
        /**
         * The static type ({@link #staticType}) of each reference local whose type is known, by
         * slot: a class, or an array type, which is tracked exactly because {@code baload} and
         * {@code bastore} take the element type from it. A class type bounds the value's class
         * from above ({@link #refuseBoxIdentity}).
         */
        private Map<Integer, String> localTypes = new HashMap<>();
        /** Where each stack value comes from, as far as nullness goes; see {@link Provenance}. */
        private List<Provenance> stackProvenance = new ArrayList<>();
        /**
         * The local slots proven never null on every path to the current point: stored a
         * {@link Provenance#NON_NULL} value last, on each path. Cleared at a block whose
         * predecessors are not all translated yet (a loop header or an exception handler), where
         * a load falls back to the whole-method judgement of {@link #neverNullSlots}.
         */
        private Set<Integer> provenNonNull = new java.util.HashSet<>();
        /** The entry state of each block, the meet over its translated predecessors. */
        private final Map<Integer, Set<Integer>> entryProvenNonNull = new HashMap<>();
        private final Map<Integer, List<Provenance>> entryStackProvenance = new HashMap<>();
        /** The blocks translated so far, for the fallback above. */
        private final Set<Integer> translated = new java.util.HashSet<>();
        /**
         * The sources of every store to each local slot, {@link Provenance#UNKNOWN} for a
         * reference parameter: a slot is never null when all of them are (greatest fixpoint).
         */
        private final Map<Integer, List<Provenance>> slotStores = new HashMap<>();
        /** String uses loaded from a local on a path not yet proven, judged once every store to the slot is known. */
        private final List<Map.Entry<Integer, FileRange>> operandSlots = new ArrayList<>();
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
            code.findAttribute(Attributes.stackMapTable()).ifPresent(table -> {
                for (var frame : table.entries()) {
                    frames.put(code.labelToBci(frame.target()), frame);
                }
            });
            for (Trap t : cfg.excs) {
                handlerBlocks.add(t.handlerBlk());
            }
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
            var done = translated;
            // `this` is never null; a reference parameter may be. Mutable: block 0 may be a loop header.
            entryProvenNonNull.put(0, new java.util.HashSet<>(method.flags().has(AccessFlag.STATIC) ? Set.of() : Set.of(0)));
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
                    meetEntryProvenance(s);
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
                slotStores.computeIfAbsent(0, x -> new ArrayList<>()).add(Provenance.NON_NULL); // this
                slot = 1;
                arg = 1;
            }
            for (var p : method.methodTypeSymbol().parameterList()) {
                Kind k = kindOf(p, methodRange);
                copies.add(new Cmd.Set(localVar(slot, k), Optional.of(new Expr.Var("arg" + arg)), methodRange));
                if (k == Kind.A) {
                    slotStores.computeIfAbsent(slot, x -> new ArrayList<>()).add(Provenance.UNKNOWN);
                }
                slot += k == Kind.J ? 2 : 1;
                arg++;
            }
            var neverNull = neverNullSlots();
            for (var operand : operandSlots) {
                if (!neverNull.contains(operand.getKey())) {
                    throw refuse(MAYBE_NULL_STRING, operand.getValue());
                }
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
            stackProvenance.add(Provenance.UNKNOWN);
            return new Expr.Var(v);
        }

        private String pushTarget(Kind k, @Nullable String type) {
            String v = stackVar(stack.size(), k);
            stack.add(k);
            stackTypes.add(type);
            stackProvenance.add(Provenance.UNKNOWN);
            return v;
        }

        /**
         * Whether a reference value is known not to be null: an object this
         * method allocated or a resolved string constant ({@link #NON_NULL}),
         * a local's value not proven on the path, judged by every store to its
         * slot in the method ({@link Slot}), or anything else, such as a field,
         * a call result or a merge of such paths ({@link #UNKNOWN}). The
         * translation has no null, so an operation that reads a field of a
         * value that may be null is refused rather than left to stop the run.
         */
        private sealed interface Provenance {
            Provenance NON_NULL = new NonNull();
            Provenance UNKNOWN = new Unknown();

            record NonNull() implements Provenance {
            }

            record Unknown() implements Provenance {
            }

            record Slot(int slot) implements Provenance {
            }
        }

        private record Value(Kind kind, Expr expr, @Nullable String type, Provenance provenance) {
            Value(Kind kind, Expr expr, @Nullable String type) {
                this(kind, expr, type, Provenance.UNKNOWN);
            }
        }

        /** Marks the value just pushed as never null. */
        private void markNonNull() {
            stackProvenance.set(stackProvenance.size() - 1, Provenance.NON_NULL);
        }

        private void markProvenance(Provenance p) {
            stackProvenance.set(stackProvenance.size() - 1, p);
        }

        /** The meet of two facts about one value: never null only when both say so. */
        private static Provenance meet(Provenance a, Provenance b) {
            return a.equals(b) ? a : Provenance.UNKNOWN;
        }

        /** Folds the state at the end of the current block into the entry state of successor {@code s}. */
        private void meetEntryProvenance(int s) {
            var proven = entryProvenNonNull.get(s);
            if (proven == null) {
                entryProvenNonNull.put(s, new java.util.HashSet<>(provenNonNull));
            } else {
                proven.retainAll(provenNonNull);
            }
            var onStack = entryStackProvenance.get(s);
            if (onStack == null) {
                entryStackProvenance.put(s, new ArrayList<>(stackProvenance));
            } else {
                for (int i = 0; i < onStack.size() && i < stackProvenance.size(); i++) {
                    onStack.set(i, meet(onStack.get(i), stackProvenance.get(i)));
                }
            }
        }

        /**
         * The state at the entry of block {@code b}: the meet over its predecessors when all of them
         * are translated; otherwise (a loop header, an exception handler) nothing is proven on the
         * path, and loads defer to the whole-method rule.
         */
        private void enterProvenance(BasicBlock b) {
            boolean complete = !handlerBlocks.contains(b.id) && translated.containsAll(b.preds);
            var proven = entryProvenNonNull.get(b.id);
            provenNonNull = complete && proven != null ? new java.util.HashSet<>(proven) : new java.util.HashSet<>();
            var onStack = entryStackProvenance.get(b.id);
            stackProvenance = complete && onStack != null && onStack.size() == stack.size()
                    ? new ArrayList<>(onStack)
                    : new ArrayList<>(java.util.Collections.nCopies(stack.size(), Provenance.UNKNOWN));
        }

        /**
         * Accepts a String value that is never null, or defers a local not proven on the path to the
         * whole-method rule; refuses anything else ({@link #MAYBE_NULL_STRING}).
         */
        private void requireNonNull(Value v, FileRange at) {
            switch (v.provenance) {
                case Provenance.NonNull n -> { }
                case Provenance.Slot sl -> operandSlots.add(Map.entry(sl.slot(), at));
                case Provenance.Unknown u -> throw refuse(MAYBE_NULL_STRING, at);
            }
        }

        /** The slots whose every store is never null, given the stores seen in the whole method. */
        private Set<Integer> neverNullSlots() {
            var never = new java.util.HashSet<>(slotStores.keySet());
            boolean changed = true;
            while (changed) {
                changed = false;
                for (var e : slotStores.entrySet()) {
                    if (!never.contains(e.getKey())) {
                        continue;
                    }
                    for (var p : e.getValue()) {
                        boolean ok = p instanceof Provenance.NonNull || p instanceof Provenance.Slot sl && never.contains(sl.slot());
                        if (!ok) {
                            never.remove(e.getKey());
                            changed = true;
                            break;
                        }
                    }
                }
            }
            return never;
        }

        private Value pop() {
            int d = stack.size() - 1;
            Kind k = stack.remove(d);
            String t = stackTypes.remove(d);
            Provenance p = stackProvenance.remove(d);
            return new Value(k, new Expr.Var("s" + d + k), t, p);
        }

        private Value snapshot(Value v, FileRange at) {
            String t = temp(v.kind.stackTy());
            cmds.add(new Cmd.Set(t, Optional.of(v.expr), at));
            return new Value(v.kind, new Expr.Var(t), v.type, v.provenance);
        }

        private void pushValues(List<Value> values, FileRange at) {
            for (var v : values) {
                push(v.kind, v.expr, v.type, at);
                markProvenance(v.provenance);
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
            if (cfg.seq.stream().anyMatch(el -> el instanceof ConstantInstruction ci && ci.constantValue() instanceof String
                    || el instanceof InvokeInstruction inv && isStringIntern(inv))) {
                stringGuard(methodRange);
            }
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

        /**
         * The guard that runs {@link #STRING_INIT} once, in the prologue of a
         * method that uses a string constant:
         * <pre>
         * if (!ready) { ready := true; $string$init(); }
         * </pre>
         * The flag is a global, so the objects allocated by one method are the
         * ones every method reads. The procedure allocates and writes fields
         * only, so it cannot throw and the flag's position does not matter.
         */
        private void stringGuard(FileRange at) {
            String flag = declareFlag(STRING_READY, at);
            String init = freshLabel();
            String cont = freshLabel();
            seal(new Transfer.CondGoto(new Expr.Var(flag), cont, init, at));
            startBlock(init);
            cmds.add(new Cmd.Set(flag, Optional.of(new Expr.Bool(true)), at));
            cmds.add(new Cmd.Call(List.of(), STRING_INIT, List.of(), at));
            seal(new Transfer.Goto(cont, at));
            startBlock(cont);
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
            cmds.add(new Cmd.InstanceOf(isError, new Expr.Var(caught), new Ty.Named(ERROR), at));
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
            String declaring = ClassInitialization.declaringClassOfStatic(resolver, target, name);
            if (declaring == null) {
                if (programClasses.containsKey(target)) {
                    throw refuse("fields of library classes", at);
                }
                if (!read) {
                    throw refuse("writes to static fields of library classes", at);
                }
                if (interpret && type.isArray()) {
                    // No translated initializer assigns the field, so a run would stop at the first use of the array.
                    throw refuse("arrays in static fields of library classes", at);
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
            tyOf(type, at);   // refuses a type the translation has none for, by name, before any guard is emitted
            if (read) {
                initialize(declaring, at);
                String global = declareGlobal(declaring, name, type, at);
                staticNotes.recordStaticRead(src.uri, lineOf(at));
                push(kindOf(type, at), new Expr.Var(global), staticType(type), at);
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
            enterLocalTypes(b);
            enterProvenance(b);
            startBlock("b" + b.id);
            for (int idx : b.instrs) {
                CodeElement el = cfg.seq.get(idx);
                if (el instanceof Instruction in) {
                    instruction(in, idx, b);
                }
            }
            exitTypes.put(b.id, localTypes);
            var last = b.last;
            boolean terminated = last instanceof BranchInstruction || last instanceof ReturnInstruction
                    || last instanceof ThrowInstruction || last instanceof TableSwitchInstruction
                    || last instanceof LookupSwitchInstruction;
            if (!terminated) {
                FileRange at = rangeAt(b.instrs.getLast());
                seal(b.fall >= 0 ? new Transfer.Goto("b" + b.fall, at) : new Transfer.Finish(at));
            }
        }

        /**
         * Sets the reference types of the locals and of the stack values on entry to {@code b}. A
         * stack-map frame at the block's offset states them. A block without a frame is no branch
         * target: the first block starts with the parameters (the receiver's type is not tracked),
         * and a later block that is no handler and has one predecessor starts with the types that
         * predecessor ends with. Any other block starts with no local type known and no array type
         * on the stack.
         */
        private void enterLocalTypes(BasicBlock b) {
            localTypes = new HashMap<>();
            var frame = b.instrs.isEmpty() ? null : frames.get(seqToBci[b.instrs.getFirst()]);
            if (frame != null) {
                int slot = 0;
                for (var t : frame.locals()) {
                    String type = referenceType(t);
                    if (type != null) {
                        localTypes.put(slot, type);
                    }
                    slot += t == StackMapFrameInfo.SimpleVerificationTypeInfo.LONG
                            || t == StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE ? 2 : 1;
                }
                boolean aligned = frame.stack().size() == stackTypes.size();
                for (int i = 0; i < stackTypes.size(); i++) {
                    String type = aligned ? referenceType(frame.stack().get(i)) : null;
                    if (type != null || isArrayType(stackTypes.get(i))) {
                        stackTypes.set(i, type);
                    }
                }
            } else if (b.id == 0 && b.preds.isEmpty()) {
                int slot = method.flags().has(AccessFlag.STATIC) ? 0 : 1;
                for (var p : method.methodTypeSymbol().parameterList()) {
                    if (!p.isPrimitive()) {
                        localTypes.put(slot, staticType(p));
                    }
                    slot += p.descriptorString().equals("J") || p.descriptorString().equals("D") ? 2 : 1;
                }
            } else if (b.preds.size() == 1 && !handlerBlocks.contains(b.id)
                    && exitTypes.containsKey(b.preds.getFirst())) {
                localTypes.putAll(exitTypes.get(b.preds.getFirst()));
            } else {
                stackTypes.replaceAll(t -> isArrayType(t) ? null : t);
            }
        }

        /** The static type a verification type states: a class or an array type; null for the others (null, uninitialized, primitives). */
        private static @Nullable String referenceType(StackMapFrameInfo.VerificationTypeInfo t) {
            return t instanceof StackMapFrameInfo.ObjectVerificationTypeInfo o ? staticType(o.classSymbol()) : null;
        }

        private void instruction(Instruction in, int idx, BasicBlock b) {
            FileRange at = rangeAt(idx);
            switch (in) {
                case NopInstruction n -> { }
                case ConstantInstruction ci -> constant(ci, at);
                case LoadInstruction li -> {
                    Kind k = kindOf(li.typeKind(), at);
                    push(k, new Expr.Var(localVar(li.slot(), k)), k == Kind.A ? localTypes.get(li.slot()) : null, at);
                    if (k == Kind.A) {
                        markProvenance(provenNonNull.contains(li.slot()) ? Provenance.NON_NULL : new Provenance.Slot(li.slot()));
                    }
                }
                case StoreInstruction si -> {
                    Kind k = kindOf(si.typeKind(), at);
                    var v = pop();
                    cmds.add(new Cmd.Set(localVar(si.slot(), k), Optional.of(v.expr), at));
                    localTypes.remove(si.slot());
                    if (k == Kind.J) {
                        localTypes.remove(si.slot() + 1);
                    }
                    if (k == Kind.A && v.type != null) {
                        localTypes.put(si.slot(), v.type);
                    }
                    if (k == Kind.A) {
                        slotStores.computeIfAbsent(si.slot(), x -> new ArrayList<>()).add(v.provenance);
                        if (v.provenance instanceof Provenance.NonNull) {
                            provenNonNull.add(si.slot());
                        } else {
                            provenNonNull.remove(si.slot());
                        }
                    } else {
                        provenNonNull.remove(si.slot());
                        if (k == Kind.J) {
                            provenNonNull.remove(si.slot() + 1);
                        }
                    }
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
                    markNonNull();
                }
                case NewPrimitiveArrayInstruction na when interpret -> newArray(na, at);
                case NewReferenceArrayInstruction nr when interpret ->
                        throw refuse(nr.componentType().asSymbol().isArray()
                                ? "multi-dimensional arrays" : "arrays of references", at);
                case NewMultiArrayInstruction nm when interpret -> throw refuse("multi-dimensional arrays", at);
                case ArrayLoadInstruction al when interpret -> {
                    var index = pop();
                    var array = pop();
                    Ty stored = elementTy(elementOf(al.typeKind(), array, at), at);
                    Expr i = checkedIndex(array, index, at);
                    cmds.add(new Cmd.ReadElem(pushTarget(kindOf(al.typeKind(), at), null), array.expr, i, stored, at));
                }
                case ArrayStoreInstruction as when interpret -> {
                    var value = pop();
                    var index = pop();
                    var array = pop();
                    String elem = elementOf(as.typeKind(), array, at);
                    Expr i = checkedIndex(array, index, at);
                    cmds.add(new Cmd.WriteElem(array.expr, i, narrowed(elem, value.expr), elementTy(elem, at), at));
                }
                case FieldInstruction fi -> field(fi, at);
                case InvokeInstruction inv -> invoke(inv, at);
                case InvokeDynamicInstruction indy -> invokeDynamic(indy, at);
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
            Ty stored = arrayType(elem.arrayType(), at).elem();
            var count = pop();
            throwIf(bv(Kind.I, "SLt", count.expr, lit(0)), "java.lang.NegativeArraySizeException", at);
            cmds.add(new Cmd.NewArray(pushTarget(Kind.A, staticType(elem.arrayType())), stored,
                    toInt(32, count.expr), at));
        }

        /**
         * The descriptor of the element type of the array that a load or store of kind {@code tk}
         * accesses. {@code baload} and {@code bastore} access both {@code boolean[]} and
         * {@code byte[]}, so there the static type of {@code array} decides.
         */
        private String elementOf(TypeKind tk, Value array, FileRange at) {
            return switch (tk) {
                case BOOLEAN, BYTE -> {
                    if ("[Z".equals(array.type)) {
                        yield "Z";
                    }
                    if ("[B".equals(array.type)) {
                        yield "B";
                    }
                    throw refuse("baload and bastore on an array whose static type is not known", at);
                }
                case SHORT -> "S";
                case CHAR -> "C";
                case INT -> "I";
                case LONG -> "J";
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
         * The value a store into an array of {@code elem} elements writes: {@code bastore},
         * {@code castore} and {@code sastore} keep the low bits of the {@code int} on the stack,
         * sign- or zero-extended, and a {@code bastore} into a {@code boolean[]} its lowest bit.
         */
        private Expr narrowed(String elem, Expr value) {
            return switch (elem) {
                case "Z" -> bv(Kind.I, "And", value, lit(1));
                case "B" -> toBv(32, op("Bv8.ToInt", toBv(8, toInt(32, value))));
                case "S" -> toBv(32, op("Bv16.ToInt", toBv(16, toInt(32, value))));
                case "C" -> toBv(32, op("Bv16.ToUInt", toBv(16, toInt(32, value))));
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
                    String global = stringLiteral(s, at);
                    if (internUsed) {
                        resolveLiteral(global, at);
                    }
                    String t = pushTarget(Kind.A, STRING);
                    cmds.add(new Cmd.Set(t, Optional.of(new Expr.Var(global)), at));
                    markNonNull();
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
                String v = pushTarget(kindOf(type, at), staticType(type));
                cmds.add(new Cmd.ReadField(v, obj.expr, declaredIn, name, at));
            } else {
                var value = pop();
                var obj = pop();
                cmds.add(new Cmd.WriteField(obj.expr, declaredIn, name, value.expr, at));
            }
        }

        private void typeCheck(TypeCheckInstruction tc, FileRange at) {
            ClassDesc t = tc.type().asSymbol();
            Ty c;
            if (t.isArray()) {
                c = arrayType(t, at);
            } else {
                referenced.add(classFqn(t));
                c = new Ty.Named(classFqn(t));
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
                stackTypes.set(d, staticType(t));
            }
        }

        private void branch(BranchInstruction br, BasicBlock b, FileRange at) {
            Opcode o = br.opcode();
            if (o == Opcode.GOTO || o == Opcode.GOTO_W) {
                seal(new Transfer.Goto("b" + b.gotoTarget, at));
                return;
            }
            if (o == Opcode.IF_ACMPEQ || o == Opcode.IF_ACMPNE) {
                refuseBoxIdentity(o == Opcode.IF_ACMPEQ ? "==" : "!=", at);
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
            if (target.equals(STRING) && !isStatic && stringMethod(name, desc, at)) {
                return;
            }
            if (libraryMethod(target, name, desc, isStatic, at)) {
                return;
            }
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
                    && (target.equals(OBJECT) || target.equals(ENUM) || target.equals(RECORD)
                            || resolver.isThrowableSubtype(target))) {
                // Nothing of the object's state to set (the modelled constructors are in libraryMethod).
                return;
            }
            var lhs = new ArrayList<String>();
            if (!desc.returnType().descriptorString().equals("V")) {
                var rt = desc.returnType();
                lhs.add(pushTarget(kindOf(rt, at), staticType(rt)));
            }
            if (declaringIn == null && interpret) {
                throw refuse("calls to library methods (" + target + "." + name + ") when interpreting", at);
            }
            MethodModel callee = declaringIn == null ? null : declared(programClasses.get(declaringIn), name, desc, isStatic);
            if (callee != null && isGeneratedRecordMember(programClasses.get(declaringIn), callee)) {
                throw refuse("the " + name + " javac generates for a record", at);
            }
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

        /**
         * {@code invokedynamic}: only javac's lowering of string concatenation
         * (JLS 15.18.1), a {@code StringConcatFactory} bootstrap, whose recipe
         * ({@link ConcatRecipe}) interleaves literal text and the arguments.
         * Each argument is converted to text (JLS 5.1.11) and the texts are
         * joined with {@code Str.Concat} into the {@code $value} of a new
         * String object: a concatenation is never a pooled literal, as javac
         * folds constant operands before emitting the call. Converted: String
         * arguments (their text) and {@code boolean}; any other type is refused
         * until the Core offers a text form of numbers and characters.
         */
        private void invokeDynamic(InvokeDynamicInstruction indy, FileRange at) {
            String name = indy.name().stringValue();
            var bsm = indy.invokedynamic().bootstrap();
            if (!bsm.bootstrapMethod().reference().owner().asInternalName().equals("java/lang/invoke/StringConcatFactory")
                    || !(name.equals("makeConcatWithConstants") || name.equals("makeConcat"))) {
                throw refuse("invokedynamic " + name, at);
            }
            MethodTypeDesc desc = indy.typeSymbol();
            String recipe = null;
            var constants = new ArrayList<String>();
            if (name.equals("makeConcatWithConstants")) {
                if (bsm.arguments().isEmpty() || !(bsm.arguments().get(0) instanceof StringEntry r)) {
                    throw refuse("a string concatenation without a recipe", at);
                }
                recipe = r.stringValue();
                for (var c : bsm.arguments().subList(1, bsm.arguments().size())) {
                    if (!(c instanceof StringEntry se)) {
                        throw refuse("a non-String constant in a string concatenation", at);
                    }
                    constants.add(se.stringValue());
                }
            }
            List<ConcatRecipe.Part> parts;
            try {
                parts = ConcatRecipe.parts(recipe, desc.parameterCount(), constants);
            } catch (IllegalArgumentException e) {
                throw refuse("a string concatenation whose " + e.getMessage(), at);
            }
            var args = new ArrayList<Value>();
            for (int i = 0; i < desc.parameterCount(); i++) {
                args.addFirst(pop());
            }
            var texts = new ArrayList<Expr>();
            for (int i = 0; i < args.size(); i++) {
                texts.add(textOf(args.get(i), desc.parameterType(i), at));
            }
            Expr text = new Expr.Str("");
            boolean first = true;
            for (var part : parts) {
                Expr e = switch (part) {
                    case ConcatRecipe.Part.Text t -> new Expr.Str(t.text());
                    case ConcatRecipe.Part.Arg a -> texts.get(a.index());
                };
                text = first ? e : op("Str.Concat", text, e);
                first = false;
            }
            String joined = temp(new Ty.String_());
            cmds.add(new Cmd.Set(joined, Optional.of(text), at));
            newString(new Expr.Var(joined), at);
        }

        /** A new String object with the text {@code text}, pushed: not a pooled literal, so {@code !=} to every other String. */
        private void newString(Expr text, FileRange at) {
            referenced.add(STRING);
            initialize(STRING, at);
            String result = pushTarget(Kind.A, STRING);
            cmds.add(new Cmd.New(result, STRING, at));
            cmds.add(new Cmd.WriteField(new Expr.Var(result), STRING, STRING_VALUE, text, at));
            markNonNull();
        }

        /**
         * The text of a concatenation operand of static type {@code type}
         * (JLS 5.1.11). A String operand that may be null would read a field
         * of nothing, so it is refused ({@link #requireNonNull}); a null
         * String would concatenate as {@code "null"}, which needs a null in
         * the translation first.
         */
        private Expr textOf(Value v, ClassDesc type, FileRange at) {
            return switch (type.descriptorString()) {
                case "Ljava/lang/String;" -> {
                    requireNonNull(v, at);
                    yield stringValue(v.expr, at);
                }
                case "Z" -> new Expr.Ite(isZero(v.expr), new Expr.Str("false"), new Expr.Str("true"));
                default -> throw refuse("string conversion of " + type.displayName() + " in a concatenation", at);
            };
        }

        /**
         * The {@code java.lang.String} methods the translation models inline,
         * on the receiver's {@code $value}; the operands are popped and the
         * result pushed. Returns false for a method it does not model.
         */
        private boolean stringMethod(String name, MethodTypeDesc desc, FileRange at) {
            switch (name + desc.descriptorString()) {
                case "length()I" -> stringLength(at);
                case "equals(Ljava/lang/Object;)Z" -> stringEquals(at);
                case "<init>()V" -> stringConstructor(Optional.empty(), at);
                case "<init>(Ljava/lang/String;)V" -> stringConstructor(Optional.of(pop()), at);
                case "intern()Ljava/lang/String;" -> stringIntern(at);
                default -> {
                    return false;
                }
            }
            return true;
        }

        /** The text of the String object {@code s}, read into a fresh {@code string} temporary. */
        private Expr stringValue(Expr s, FileRange at) {
            String v = temp(new Ty.String_());
            cmds.add(new Cmd.ReadField(v, s, STRING, STRING_VALUE, at));
            return new Expr.Var(v);
        }

        /**
         * {@code s.length()}: {@code Str.Length} of the text, narrowed to
         * {@code bv32}. {@code Str.Length} counts code points where Java
         * counts UTF-16 units, so the two differ on text outside the Basic
         * Multilingual Plane.
         */
        private void stringLength(FileRange at) {
            var s = pop();
            push(Kind.I, toBv(32, op("Str.Length", stringValue(s.expr, at))), null, at);
        }

        /**
         * {@code s.equals(o)}: true when {@code o} is a String whose text is
         * {@code s}'s (JLS 4.3.3). The texts are compared in a block of their
         * own, entered only when {@code o} is a String, since reading
         * {@code $value} of another object is an error.
         */
        private void stringEquals(FileRange at) {
            var o = pop();
            var s = pop();
            String isString = temp(new Ty.Bool());
            cmds.add(new Cmd.InstanceOf(isString, o.expr, new Ty.Named(STRING), at));
            String result = temp(new Ty.Bool());
            cmds.add(new Cmd.Set(result, Optional.of(new Expr.Bool(false)), at));
            String compare = freshLabel();
            String cont = freshLabel();
            seal(new Transfer.CondGoto(new Expr.Var(isString), compare, cont, at));
            startBlock(compare);
            cmds.add(new Cmd.Set(result, Optional.of(new Expr.Eq(stringValue(s.expr, at), stringValue(o.expr, at))), at));
            seal(new Transfer.Goto(cont, at));
            startBlock(cont);
            push(Kind.I, boolToInt(new Expr.Var(result)), null, at);
        }

        /**
         * {@code new String()} or {@code new String(s)}: the object, already
         * allocated by {@code new}, gets the empty text or {@code s}'s. It is
         * a new object, {@code !=} to every other String (JLS 12.5) and
         * {@code equals} to {@code s}; {@code ==} to a literal of the same text
         * does not hold, since literals are pooled and this is not.
         */
        private void stringConstructor(Optional<Value> src, FileRange at) {
            var self = pop();
            src.ifPresent(v -> requireNonNull(v, at));
            Expr text = src.isPresent() ? stringValue(src.get().expr, at) : new Expr.Str("");
            cmds.add(new Cmd.WriteField(self.expr, STRING, STRING_VALUE, text, at));
        }

        /** {@code s.intern()}: a call to {@link #STRING_INTERN}, which runs under this method's {@link #stringGuard}. */
        private void stringIntern(FileRange at) {
            var s = pop();
            requireNonNull(s, at);
            declarePool(at);
            String r = pushTarget(Kind.A, STRING);
            cmds.add(new Cmd.Call(List.of(r), STRING_INTERN, List.of(s.expr), at));
            markNonNull();
        }

        /**
         * The library methods, besides String's, that the translation models
         * inline on the modelled fields of their class ({@link #libraryFields});
         * the operands are popped and the result pushed. Returns false for a
         * call it does not model, which the caller refuses or translates as a
         * call.
         *
         * <p>TEMPORARY. Recognizing JDK methods by name here and hand-writing
         * their effect is a stopgap for the interpret-only Cortex path, which
         * has no contract mechanism. The planned long-term solution replaces
         * every case below by a contract for the method (and, where wanted,
         * the library's own bytecode), translated like any other call; this
         * method and {@link #libraryFields} then go away. Modelled for now:
         * <ul>
         *   <li>{@code Integer.valueOf(int)} and {@code intValue()}: boxing
         *       and unboxing ({@link #box}, {@link #unbox}).</li>
         *   <li>{@code StringBuilder}: {@code new StringBuilder()},
         *       {@code append} of a String or boolean, {@code toString()} and
         *       {@code setLength(0)} ({@link #stringBuilderMethod}).</li>
         *   <li>A Throwable's constructors taking a message and/or a cause,
         *       {@code getMessage()} and {@code getCause()} when no program
         *       class overrides them, and {@code addSuppressed}, which records
         *       nothing: {@code getSuppressed()} is not modelled.</li>
         *   <li>{@code Enum(String, int)}, {@code name()} and
         *       {@code ordinal()}, which are final.</li>
         * </ul>
         */
        private boolean libraryMethod(String target, String name, MethodTypeDesc desc, boolean isStatic, FileRange at) {
            String key = name + desc.descriptorString();
            if (target.equals(INTEGER)) {
                switch (key) {
                    case "valueOf(I)Ljava/lang/Integer;" -> box(at);
                    case "intValue()I" -> unbox(at);
                    default -> {
                        return false;
                    }
                }
                return true;
            }
            if (target.equals(STRING_BUILDER)) {
                return stringBuilderMethod(key, desc, at);
            }
            if (target.equals(ENUM) && key.equals("<init>(Ljava/lang/String;I)V")) {
                enumConstructor(at);
                return true;
            }
            if (isEnumClass(target) && !isStatic) {
                switch (key) {
                    case "name()Ljava/lang/String;" -> enumField(ENUM_NAME, Kind.A, STRING, at);
                    case "ordinal()I" -> enumField(ENUM_ORDINAL, Kind.I, null, at);
                    default -> {
                        return false;
                    }
                }
                return true;
            }
            if (resolver.isThrowableSubtype(target) && !isStatic) {
                boolean library = !programClasses.containsKey(target);
                boolean overridden = programDeclares(name, desc);
                if (library && key.equals("<init>(Ljava/lang/String;)V")) {
                    throwableConstructor(true, false, at);
                } else if (library && key.equals("<init>(Ljava/lang/String;Ljava/lang/Throwable;)V")) {
                    throwableConstructor(true, true, at);
                } else if (library && key.equals("<init>(Ljava/lang/Throwable;)V")) {
                    throwableConstructor(false, true, at);
                } else if (key.equals("addSuppressed(Ljava/lang/Throwable;)V")) {
                    pop();
                    pop();
                } else if (!overridden && key.equals("getMessage()Ljava/lang/String;")) {
                    throwableField(THROWABLE_MESSAGE, STRING, at);
                } else if (!overridden && key.equals("getCause()Ljava/lang/Throwable;")) {
                    throwableField(THROWABLE_CAUSE, THROWABLE, at);
                } else {
                    return false;
                }
                return true;
            }
            return false;
        }

        /** Whether a program class declares an instance method {@code name} with {@code desc}'s parameters: a possible override. */
        private boolean programDeclares(String name, MethodTypeDesc desc) {
            return programClasses.values().stream().anyMatch(cm -> declared(cm, name, desc, false) != null);
        }

        /** Whether {@code fqn} is {@code java.lang.Enum} or has it on its superclass chain (an enum or a constant's body class). */
        private boolean isEnumClass(String fqn) {
            for (String c = fqn; c != null; c = resolver.superclassOf(c)) {
                if (c.equals(ENUM)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * {@code Integer.valueOf(i)}, which is also javac's boxing conversion
         * (JLS 5.1.7): a new Integer object holding {@code i}. Java reuses the
         * boxes of -128..127, so two boxes of one value may be one object
         * there and never are here; see {@link #refuseBoxIdentity}.
         */
        private void box(FileRange at) {
            var v = pop();
            referenced.add(INTEGER);
            String r = pushTarget(Kind.A, INTEGER);
            cmds.add(new Cmd.New(r, INTEGER, at));
            cmds.add(new Cmd.WriteField(new Expr.Var(r), INTEGER, STRING_VALUE, v.expr, at));
            markNonNull();
        }

        /** {@code b.intValue()}, the unboxing conversion (JLS 5.1.8): the box's value. */
        private void unbox(FileRange at) {
            var b = pop();
            String v = temp(new Ty.Bv(32));
            cmds.add(new Cmd.ReadField(v, b.expr, INTEGER, STRING_VALUE, at));
            push(Kind.I, new Expr.Var(v), null, at);
        }

        /**
         * Reference {@code ==} or {@code !=} whose operands may be Integer
         * boxes ({@link #MAY_HOLD_BOX}), in a program that boxes: Java reuses
         * the boxes of -128..127 (JLS 5.1.7), so whether two boxes are one
         * object depends on their values, while {@link #box} allocates each
         * one. A program that never boxes compares no boxes, whatever the
         * static types.
         */
        private void refuseBoxIdentity(String op, FileRange at) {
            if (!boxes) {
                return;
            }
            for (int i = stack.size() - 2; i < stack.size(); i++) {
                String t = stackTypes.get(i);
                if (t == null || MAY_HOLD_BOX.contains(t)) {
                    throw refuse("'" + op + "' on references that may be boxed values (their identity depends on the box cache)", at);
                }
            }
        }

        /**
         * {@code java.lang.StringBuilder} as a mutable text: {@code new StringBuilder()}
         * starts empty; {@code append} of one operand concatenates its text
         * form ({@link #textOf}, which refuses the types without one) and
         * returns the builder; {@code toString()}
         * is a new String of the text; {@code setLength(0)}, the idiom that
         * clears a builder, empties it (any other length would need a prefix
         * of the text, which the Core has no operation for).
         */
        private boolean stringBuilderMethod(String key, MethodTypeDesc desc, FileRange at) {
            if (key.startsWith("append(") && key.endsWith(")Ljava/lang/StringBuilder;") && desc.parameterCount() == 1) {
                key = "append";   // of any one operand: textOf refuses the types without a text form
            }
            switch (key) {
                case "<init>()V" -> {
                    var self = pop();
                    cmds.add(new Cmd.WriteField(self.expr, STRING_BUILDER, STRING_VALUE, new Expr.Str(""), at));
                }
                case "append" -> {
                    var v = pop();
                    var self = pop();
                    Expr text = textOf(v, desc.parameterType(0), at);
                    Expr current = builderText(self.expr, at);
                    cmds.add(new Cmd.WriteField(self.expr, STRING_BUILDER, STRING_VALUE, op("Str.Concat", current, text), at));
                    pushBack(self, at);
                }
                case "toString()Ljava/lang/String;" -> {
                    var self = pop();
                    newString(builderText(self.expr, at), at);
                }
                case "setLength(I)V" -> {
                    var n = pop();
                    var self = pop();
                    if (!isConstantZero(n)) {
                        throw refuse("StringBuilder.setLength with a length other than the constant 0", at);
                    }
                    cmds.add(new Cmd.WriteField(self.expr, STRING_BUILDER, STRING_VALUE, new Expr.Str(""), at));
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        /** The text of the StringBuilder {@code sb}, read into a fresh {@code string} temporary. */
        private Expr builderText(Expr sb, FileRange at) {
            String v = temp(new Ty.String_());
            cmds.add(new Cmd.ReadField(v, sb, STRING_BUILDER, STRING_VALUE, at));
            return new Expr.Var(v);
        }

        /** Whether the stack value {@code v} was set to the int constant 0 by the command just before (an {@code iconst_0}). */
        private boolean isConstantZero(Value v) {
            return !cmds.isEmpty() && cmds.getLast() instanceof Cmd.Set(String x, Optional<Expr> e, FileRange ignored)
                    && v.expr.equals(new Expr.Var(x)) && e.isPresent() && e.get().equals(lit(0));
        }

        /** Pushes a popped value again (a method returning its receiver), with its provenance. */
        private void pushBack(Value v, FileRange at) {
            String var = pushTarget(v.kind, v.type);
            if (!v.expr.equals(new Expr.Var(var))) {
                cmds.add(new Cmd.Set(var, Optional.of(v.expr), at));
            }
            markProvenance(v.provenance);
        }

        /**
         * {@code new T(message)}, {@code new T(message, cause)} or
         * {@code new T(cause)} for a library Throwable class {@code T},
         * including the {@code super(...)} call of a program exception class:
         * the modelled fields get the arguments; the object was allocated by
         * {@code new}. In Java, {@code Throwable(Throwable cause)} also sets
         * the message to {@code cause.toString()}, which has no text here:
         * the message stays unset, so a later {@code getMessage()} yields a
         * reference to no object, and a use of it stops the run.
         */
        private void throwableConstructor(boolean message, boolean cause, FileRange at) {
            Value c = cause ? pop() : null;
            Value m = message ? pop() : null;
            var self = pop();
            if (m != null) {
                cmds.add(new Cmd.WriteField(self.expr, THROWABLE, THROWABLE_MESSAGE, m.expr, at));
            }
            if (c != null) {
                cmds.add(new Cmd.WriteField(self.expr, THROWABLE, THROWABLE_CAUSE, c.expr, at));
            }
        }

        /**
         * {@code e.getMessage()} or {@code e.getCause()}: the modelled field.
         * Unset (no message or no cause given), it is a reference to no object
         * where Java has null; a use of it stops the run.
         */
        private void throwableField(String field, String type, FileRange at) {
            var self = pop();
            String r = pushTarget(Kind.A, type);
            cmds.add(new Cmd.ReadField(r, self.expr, THROWABLE, field, at));
        }

        /** {@code Enum(name, ordinal)}, called by every enum constructor (JLS 8.9.2): the modelled fields get the arguments. */
        private void enumConstructor(FileRange at) {
            var ordinal = pop();
            var name = pop();
            var self = pop();
            cmds.add(new Cmd.WriteField(self.expr, ENUM, ENUM_NAME, name.expr, at));
            cmds.add(new Cmd.WriteField(self.expr, ENUM, ENUM_ORDINAL, ordinal.expr, at));
        }

        /** {@code c.name()} or {@code c.ordinal()}: the modelled field; a name is never null. */
        private void enumField(String field, Kind kind, @Nullable String type, FileRange at) {
            var self = pop();
            String r = pushTarget(kind, type);
            cmds.add(new Cmd.ReadField(r, self.expr, ENUM, field, at));
            if (kind == Kind.A) {
                markNonNull();
            }
        }

        /**
         * Resolution of a string constant (JVMS 5.1), when the program interns:
         * the first {@code ldc} of its text interns the constant's object, so
         * if a string with that text was interned before, the constant becomes
         * that string. Later {@code ldc}s read the resolved global.
         * <pre>
         * if (!resolved) { resolved := true; lit := $string$intern(lit); }
         * </pre>
         */
        private void resolveLiteral(String global, FileRange at) {
            String flag = declareFlag(global + "$resolved", at);
            String resolve = freshLabel();
            String cont = freshLabel();
            seal(new Transfer.CondGoto(new Expr.Var(flag), cont, resolve, at));
            startBlock(resolve);
            cmds.add(new Cmd.Set(flag, Optional.of(new Expr.Bool(true)), at));
            String r = temp(new Ty.Named(STRING));
            cmds.add(new Cmd.Call(List.of(r), STRING_INTERN, List.of(new Expr.Var(global)), at));
            cmds.add(new Cmd.Set(global, Optional.of(new Expr.Var(r)), at));
            seal(new Transfer.Goto(cont, at));
            startBlock(cont);
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
