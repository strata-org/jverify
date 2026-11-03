package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.util.List;
import java.util.Optional;

/**
 * The array model: one-dimensional primitive Java arrays as the generic
 * Laurel composite {@code JArray<E>}, whose members come from the built-in
 * {@code JArray} class of {@code object-contract.java}. {@code E} is the
 * element kind's carrier: {@code int} for every integral kind (Strata rejects
 * constrained types as type arguments) and {@code bool} for {@code boolean}.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Range: a {@code JArray<int>} holding kind-{@code k} elements contains
 *       only values in {@code k}'s range. The translator owns it (checked
 *       stores, in-range zero-initialization, no array writes in contract
 *       code); loads rely on it.</li>
 *   <li>Contents: an element read depends on exactly that array's
 *       {@code contentsVersion}, and a length is fixed and in
 *       {@code 0..2^31-1}. The JArray contract owns it.</li>
 *   <li>Freshness: an allocation is distinct from every existing object.
 *       Laurel's {@code new} owns it.</li>
 *   <li>Types: every Laurel {@code JArray} receiver has the right {@code E}.
 *       The descriptor checks own it, so a violation is a refusal, never a
 *       Laurel type error.</li>
 * </ul>
 *
 * <p>The composite has exactly one field, {@code contentsVersion}, and its
 * length reads no heap, so "this array's contents may change" is exactly
 * the location {@code a#contentsVersion} ({@link #frameTarget}).
 */
final class ArrayModel {

    /** The Laurel composite sort of every array. */
    static final String SORT = "JArray";
    /** The JArray class in {@code object-contract.java}. */
    static final String JARRAY_INTERNAL = LibraryContracts.JARRAY_INTERNAL;
    /** The composite's single type parameter, as the JArray class declares it. */
    static final String ELEMENT_TYPE_PARAM = "TArrayElement";
    /** The composite's single field: the version of the array's contents. */
    static final String CONTENTS_FIELD = "contentsVersion";

    // --- Refusal texts ---

    static final String REFERENCE = "arrays of reference types are not yet supported";
    static final String MULTI = "multi-dimensional arrays are not yet supported";
    static final String CTOR_REF = "array constructor references (int[]::new) are not yet supported";
    static final String ARRAYCOPY = "System.arraycopy is not yet supported";
    static final String ARRAYS_UTIL = "java.util.Arrays methods are not yet supported";
    static final String CLONE = "array clone() is not yet supported";
    static final String AS_OBJECT = "an array used as an Object is not yet supported";
    static final String CAST = "casts and instanceof tests on array types are not yet supported";
    static final String NULLABLE = "@Nullable arrays are not yet supported";
    static final String QUANTIFIER = "quantifiers over arrays are not yet supported";
    static final String UNTYPED_LOCAL = "array access through a local variable with no recorded type"
            + " (for example an enhanced for loop) is not yet supported";
    static final String UNKNOWN_ELEMENT = "array element type could not be determined";
    static final String NO_DEBUG_INFO = "arrays in code compiled without -g are not yet supported";
    static final String CONDITIONAL = "an array allocation or write inside a condition or conditional"
            + " expression is not yet supported";
    static final String SPEC_WRITE = "an array allocation or write inside a specification is not yet supported";
    static final String CONTRACT_WRITE = "an array allocation or write inside library contract code"
            + " is not yet supported";
    static final String VALUE_USE = "an array element assignment or increment used as a value"
            + " is not yet supported";
    static final String NON_BOOLEAN_STORE = "a non-boolean value stored into a boolean[] is not yet supported";
    static final String UNAVAILABLE = "the JArray array model is unavailable";

    // --- Generated procedures ---

    /** The element load helper prefix: {@code java?array_load_<suffix>(x: int): <range type>}. */
    static final String LOAD_HELPER_PREFIX = "java?array_load_";
    /**
     * The transparent element load helper prefix for a specification or a
     * transparent {@code @Transparent} body: {@code
     * java?array_pure_load_<suffix>(x: int): <range type>}, the element
     * clamped to the kind's range.
     */
    static final String PURE_LOAD_HELPER_PREFIX = "java?array_pure_load_";
    /** The element store helper prefix: {@code java?array_store_<suffix>(x: int): int}, range-checked. */
    static final String STORE_HELPER_PREFIX = "java?array_store_";
    /**
     * True for a {@code Nullable} annotation of any package (the class
     * descriptor ends in {@code /Nullable;}, or is {@code LNullable;}).
     */
    static boolean isNullableAnnotation(String classDescriptor) {
        return classDescriptor.endsWith("/Nullable;") || classDescriptor.equals("LNullable;");
    }

    /** A one-dimensional primitive array's element kind (float and double are not supported). */
    enum ElementKind {
        INT8('B', "int8", "byte", -128L, 127L),
        CHAR('C', "char", "char", 0L, 65535L),
        INT16('S', "int16", "short", -32768L, 32767L),
        INT32('I', "int32", "int", LaurelFactory.BOUNDS.get("int32")[0], LaurelFactory.BOUNDS.get("int32")[1]),
        INT64('J', "int64", "long", LaurelFactory.BOUNDS.get("int64")[0], LaurelFactory.BOUNDS.get("int64")[1]),
        BOOL('Z', null, "boolean", null, null);

        private final char code;
        private final String rangeType;
        private final String javaName;
        private final Long lo;
        private final Long hi;

        ElementKind(char code, String rangeType, String javaName, Long lo, Long hi) {
            this.code = code;
            this.rangeType = rangeType;
            this.javaName = javaName;
            this.lo = lo;
            this.hi = hi;
        }

        /** The array descriptor, e.g. {@code [I}. */
        String descriptor() {
            return "[" + code;
        }

        /** The element descriptor, e.g. {@code I}. */
        String elementDescriptor() {
            return String.valueOf(code);
        }

        /**
         * The Laurel element type of the composite: {@code int}, or {@code bool}
         * for boolean. Temporary: Strata rejects constrained types such as
         * {@code int8} as type arguments (B1), and an {@code int32} argument
         * for a {@code T} parameter fails after monomorphization (B2), so an
         * integral array carries unbounded {@code int}, and the translator
         * checks every store and assumes every load in the kind's range. Once
         * B1 and B2 are fixed, the carrier is the kind's range type
         * ({@code JArray<int8>}, ...) and Strata checks and assumes the range.
         */
        HighType carrier() {
            return this == BOOL ? new HighType.TBool() : new HighType.TInt();
        }

        /** The Laurel constrained type of an element value ({@code int32}, ...); null for boolean. */
        String rangeType() {
            return rangeType;
        }

        /** The Java zero value: {@code 0}, or {@code false} for boolean. */
        StmtExpr zero() {
            return this == BOOL ? new StmtExpr.LiteralBool(false) : new StmtExpr.LiteralInt(0);
        }

        /** The load/store helper name suffix ({@code java?array_load_<suffix>}); null for boolean. */
        String helperSuffix() {
            return rangeType;
        }

        /** The Java element type name: {@code byte}, {@code char}, ... */
        String javaName() {
            return javaName;
        }

        /** The smallest element value; null for boolean. */
        Long lo() {
            return lo;
        }

        /** The largest element value; null for boolean. */
        Long hi() {
            return hi;
        }
    }

    private final LibraryContracts contracts;
    private final ClassResolver resolver;
    private final CompositeModel composites;
    /** The shape check's result once run (empty when the model is usable); null before. */
    private Optional<String> checkedShape;
    /** {@link #enabled}'s value once {@link #computeEnabled} has run; null before. */
    private Boolean enabled;
    /**
     * True while translating a method whose code has locals but no
     * LocalVariableTable ({@code MethodBody.hasLocalsWithoutDebugInfo}): set
     * and restored around each method by the compiler.
     */
    boolean inCodeWithoutDebugInfo;

    /**
     * Refuse an array type or operation in code compiled without {@code -g}:
     * its locals have no recorded types, so array values in them have no
     * element kind.
     *
     * @throws JavaViolationException {@link #NO_DEBUG_INFO}
     */
    void refuseInCodeWithoutDebugInfo() {
        if (inCodeWithoutDebugInfo) {
            throw new JavaViolationException(NO_DEBUG_INFO);
        }
    }

    ArrayModel(LibraryContracts contracts, ClassResolver resolver, CompositeModel composites) {
        this.contracts = contracts;
        this.resolver = resolver;
        this.composites = composites;
    }

    // --- Element kinds ---

    /** The element kind of a one-dimensional primitive array descriptor, or null (also for {@code [F}, {@code [D}). */
    static ElementKind kindOfDescriptor(String desc) {
        if (desc == null || desc.length() != 2 || desc.charAt(0) != '[') {
            return null;
        }
        for (var k : ElementKind.values()) {
            if (k.code == desc.charAt(1)) {
                return k;
            }
        }
        return null;
    }

    /** The element kind whose helper name suffix ({@link ElementKind#helperSuffix}) is {@code suffix}. */
    static ElementKind kindOfHelperSuffix(String suffix) {
        for (var k : ElementKind.values()) {
            if (suffix.equals(k.helperSuffix())) {
                return k;
            }
        }
        throw new IllegalArgumentException(suffix);
    }

    /**
     * The element kind a {@code newarray} creates.
     *
     * @throws JavaViolationException {@code Unsupported type: float[]} / {@code double[]}
     */
    static ElementKind ofTypeKind(java.lang.classfile.TypeKind typeKind) {
        return switch (typeKind) {
            case BYTE -> ElementKind.INT8;
            case CHAR -> ElementKind.CHAR;
            case SHORT -> ElementKind.INT16;
            case INT -> ElementKind.INT32;
            case LONG -> ElementKind.INT64;
            case BOOLEAN -> ElementKind.BOOL;
            case FLOAT -> throw new JavaViolationException("Unsupported type: float[]");
            case DOUBLE -> throw new JavaViolationException("Unsupported type: double[]");
            default -> throw new JavaViolationException(UNKNOWN_ELEMENT);
        };
    }

    /**
     * The element kind of an array type, from its display ({@code int[]},
     * {@code String[]}, {@code T[]}, {@code int[][]}). Refuses every array
     * type the model does not cover, and an {@code @Nat}/{@code @Unbounded}
     * annotation on the array type itself.
     *
     * @throws IllegalArgumentException when {@code t} is not an array type
     */
    static ElementKind kindOfType(TypeRef t) {
        if (t.kind() != TypeRef.Kind.ARRAY || !t.display().endsWith("[]")) {
            throw new IllegalArgumentException("not an array type: " + t.display());
        }
        String element = t.display().substring(0, t.display().length() - 2).strip();
        ElementKind kind = switch (element) {
            case "byte" -> ElementKind.INT8;
            case "char" -> ElementKind.CHAR;
            case "short" -> ElementKind.INT16;
            case "int" -> ElementKind.INT32;
            case "long" -> ElementKind.INT64;
            case "boolean" -> ElementKind.BOOL;
            case "float", "double" -> throw new JavaViolationException("Unsupported type: " + t.display());
            default -> throw new JavaViolationException(element.endsWith("[]") ? MULTI : REFERENCE);
        };
        if (t.isNat() || t.isUnbounded()) {
            throw new JavaViolationException("Unsupported type: " + t.display());
        }
        return kind;
    }

    // --- Laurel types ---

    /**
     * The Laurel type of a one-dimensional primitive array type: {@code JArray<int>} or {@code JArray<bool>}.
     *
     * @throws JavaViolationException {@link #NO_DEBUG_INFO} in code compiled
     *         without {@code -g}, else the refusal of {@link #kindOfType}
     */
    HighType laurelType(TypeRef t) {
        refuseInCodeWithoutDebugInfo();
        return jarrayOf(LaurelFactory.node(kindOfType(t).carrier()));
    }

    /**
     * The refusal for an array instruction the model does not cover, by
     * opcode: reference-element loads and stores ({@link #MULTI} when the
     * receiver's descriptor is an array of arrays, else {@link #REFERENCE})
     * and {@code float}/{@code double} element loads and stores; null for the
     * six primitive kinds.
     */
    static String unmodelledAccess(java.lang.classfile.Opcode op, String receiverDesc) {
        return switch (op) {
            case AALOAD, AASTORE -> receiverDesc != null && receiverDesc.startsWith("[[") ? MULTI : REFERENCE;
            case FALOAD, FASTORE -> "Unsupported type: float[]";
            case DALOAD, DASTORE -> "Unsupported type: double[]";
            default -> null;
        };
    }

    /**
     * {@code JArray<element>}, registering the composite, its type parameter
     * and its field on first use.
     */
    HighType jarrayOf(AstNode<HighType> element) {
        registerComposite();
        return new HighType.Applied(LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(SORT))),
                List.of(element));
    }

    /**
     * Declare the {@code JArray} composite: referenced, with its type
     * parameter {@code TArrayElement} and its field
     * {@code contentsVersion: int}. Idempotent.
     */
    void registerComposite() {
        composites.referencedCompositeTypes.add(SORT);
        composites.compositeTypeParams.putIfAbsent(SORT, List.of(ELEMENT_TYPE_PARAM));
        composites.compositeFields
                .computeIfAbsent(SORT, k -> new java.util.LinkedHashMap<>())
                .putIfAbsent(CONTENTS_FIELD, new org.strata.jverify.laurel.Field(
                        LaurelFactory.ident(CONTENTS_FIELD), /*isMutable*/ true,
                        LaurelFactory.node(new HighType.TInt()), Optional.empty()));
    }

    /** The type of {@code self?} in a JArray member: {@code JArray<TArrayElement>}. */
    HighType selfType() {
        return jarrayOf(LaurelFactory.node(new HighType.TVar(LaurelFactory.ident(ELEMENT_TYPE_PARAM))));
    }

    // --- Availability and members ---

    /**
     * Decide {@link #enabled} from the current program class set; called
     * once the set is final (in {@code analyze}, after every program class
     * is registered). Never loads the contracts.
     */
    void computeEnabled() {
        boolean result = !contracts.disabled();
        for (String fqn : resolver.programClassModels.keySet()) {
            if (JavaNames.simpleName(fqn).equals(SORT)) {
                result = false;
                break;
            }
        }
        enabled = result;
    }

    /**
     * True iff programs translate arrays through the model: library contracts
     * are in use and no program class (nested ones included) has the simple
     * name {@code JArray}, which would collide with the composite sort.
     * The value {@link #computeEnabled} cached.
     *
     * @throws IllegalStateException before {@link #computeEnabled} has run
     */
    boolean enabled() {
        if (enabled == null) {
            throw new IllegalStateException("ArrayModel.enabled() read before computeEnabled()");
        }
        return enabled;
    }

    /**
     * The JArray instance member {@code name} ({@code length}, {@code get},
     * {@code set}), after checking the loaded JArray class has the shape the
     * model relies on.
     *
     * @throws JavaViolationException {@link #UNAVAILABLE} when the contracts
     *         did not load, the class is missing or differently shaped, or it
     *         has no such member
     */
    ContractMember member(String name) {
        ContractMember m = contracts.arrayMember(name);
        if (checkedShape == null && contracts.loaded()) {
            checkedShape = Optional.ofNullable(shapeProblem(contracts.classBytes(JARRAY_INTERNAL)));
        }
        if (m == null || checkedShape == null || checkedShape.isPresent()) {
            throw new JavaViolationException(UNAVAILABLE);
        }
        return m;
    }

    /**
     * What makes {@code cm} unusable as the JArray model, or null when its
     * shape is right: exactly the class type parameter {@code TArrayElement},
     * exactly one instance field ({@code int contentsVersion}), and the
     * instance methods {@code length()I}, {@code get(I)Ljava/lang/Object;}
     * and {@code set(ILjava/lang/Object;)V}.
     */
    static String shapeProblem(ClassModel cm) {
        if (cm == null) {
            return "no JArray class";
        }
        var typeParams = Declarations.typeParamNamesOf(cm);
        if (!typeParams.equals(List.of(ELEMENT_TYPE_PARAM))) {
            return "class type parameters " + typeParams + ", expected [" + ELEMENT_TYPE_PARAM + "]";
        }
        var instanceFields = cm.fields().stream()
                .filter(f -> (f.flags().flagsMask() & ClassFile.ACC_STATIC) == 0)
                .toList();
        if (instanceFields.size() != 1
                || !instanceFields.getFirst().fieldName().stringValue().equals(CONTENTS_FIELD)
                || !instanceFields.getFirst().fieldType().stringValue().equals("I")) {
            return "instance fields " + instanceFields.stream()
                    .map(f -> f.fieldType().stringValue() + " " + f.fieldName().stringValue()).toList()
                    + ", expected [I " + CONTENTS_FIELD + "]";
        }
        for (String required : List.of("length()I", "get(I)Ljava/lang/Object;", "set(ILjava/lang/Object;)V")) {
            boolean found = cm.methods().stream().anyMatch(mm ->
                    (mm.flags().flagsMask() & ClassFile.ACC_STATIC) == 0
                            && (mm.methodName().stringValue() + mm.methodType().stringValue()).equals(required));
            if (!found) {
                return "no instance method " + required;
            }
        }
        return null;
    }

    // --- Frames ---

    /** The frame location of "{@code x}'s contents may change": {@code x#contentsVersion}. */
    static AstNode<StmtExpr> frameTarget(AstNode<StmtExpr> x) {
        return new AstNode<>(new StmtExpr.Var(new Variable.Field(x, LaurelFactory.ident(CONTENTS_FIELD))),
                x.source());
    }
}
