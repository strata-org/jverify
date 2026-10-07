package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.HighType;

import java.lang.constant.ClassDesc;

/**
 * A Java type reference for the bytecode translator: the facts about a Java
 * type that the Laurel translation consumes, recovered from descriptors and
 * generic signatures.
 *
 * <p>Display parity: refusal messages embed the source-form rendering
 * ("Unsupported type: T", "Cast to List&lt;String&gt; is not yet supported"),
 * and composite sort names for library classes are SHORTENED simple names
 * ("String", not "java.lang.String") — program classes qualify to fqns via
 * {@link ClassResolver#qualifyTypeName}. Both are reproduced from
 * descriptors/signatures here, matching JFE's corpus-pinned output.
 *
 * @param kind          the shape this reference takes
 * @param display       rendering used in refusal messages (generics included)
 * @param nameWithScope the class name as Java source would print it,
 *                      type args excluded; null for non-class kinds
 * @param simpleName    the last name segment; null for non-class kinds
 * @param scoped        whether the printed name has an outer scope
 *                      ("Outer.Inner"); a scoped name is never a type var
 * @param isNat         carries a @Nat type-use annotation
 * @param isUnbounded   carries an @Unbounded type-use annotation
 * @param fqnFromContext the binary-derived fqn ('$' flattened to '.') when
 *                      this reference came from a descriptor; null for
 *                      rendered-signature references, which resolve through
 *                      the unit's imports ({@link ClassResolver#resolve})
 */
record TypeRef(Kind kind, String display, String nameWithScope, String simpleName,
               boolean scoped, boolean isNat, boolean isUnbounded, String fqnFromContext) {

    enum Kind { PRIMITIVE, CLASS, ARRAY, OTHER }

    // --- Factories ---

    /** From a JVM field descriptor, e.g. {@code I}, {@code Ljava/util/List;}, {@code [I}. */
    static TypeRef ofDescriptor(String desc, boolean isNat, boolean isUnbounded) {
        return switch (desc.charAt(0)) {
            case 'I' -> primitive("int", isNat, isUnbounded);
            case 'S' -> primitive("short", isNat, isUnbounded);
            case 'B' -> primitive("byte", isNat, isUnbounded);
            case 'J' -> primitive("long", isNat, isUnbounded);
            case 'C' -> primitive("char", isNat, isUnbounded);
            case 'Z' -> primitive("boolean", isNat, isUnbounded);
            case 'F' -> primitive("float", isNat, isUnbounded);
            case 'D' -> primitive("double", isNat, isUnbounded);
            case 'V' -> new TypeRef(Kind.OTHER, "void", null, null, false, false, false, null);
            case 'L' -> ofClassInternal(desc.substring(1, desc.length() - 1), isNat, isUnbounded);
            case '[' -> {
                TypeRef component = ofDescriptor(desc.substring(1), false, false);
                yield new TypeRef(Kind.ARRAY, component.display() + "[]", null, null, false, isNat, isUnbounded, null);
            }
            default -> new TypeRef(Kind.OTHER, desc, null, null, false, isNat, isUnbounded, null);
        };
    }

    static TypeRef ofDescriptor(String desc) {
        return ofDescriptor(desc, false, false);
    }

    static TypeRef ofClassDesc(ClassDesc cd) {
        return ofDescriptor(cd.descriptorString());
    }

    /**
     * From an internal class name, e.g. {@code java/util/List} or
     * {@code pkg/Outer$Inner}. The printed name is the shortened form: the
     * binary name's last {@code /}-segment with {@code $} flattened to
     * {@code .} — "List", "Outer.Inner".
     */
    static TypeRef ofClassInternal(String internal, boolean isNat, boolean isUnbounded) {
        int slash = internal.lastIndexOf('/');
        String tail = slash >= 0 ? internal.substring(slash + 1) : internal;
        String printed = tail.replace('$', '.');
        int lastDot = printed.lastIndexOf('.');
        String simple = lastDot >= 0 ? printed.substring(lastDot + 1) : printed;
        return new TypeRef(Kind.CLASS, printed, printed, simple, lastDot >= 0, isNat, isUnbounded,
                JavaNames.dottedFqn(internal));
    }

    /**
     * From a rendered generic-signature string ({@code
     * ClassfileQueries.renderSig} output, stored on {@link
     * org.strata.java.bytecode.frontend.Locals.Scope#genericType}),
     * e.g. {@code List<String>}, {@code T}, {@code Outer.Inner<T>[]}. The
     * name-with-scope strips type arguments; arrays classify as ARRAY.
     */
    static TypeRef ofRendered(String rendered, boolean isNat, boolean isUnbounded) {
        String r = rendered.strip();
        if (r.endsWith("[]")) {
            return new TypeRef(Kind.ARRAY, r, null, null, false, isNat, isUnbounded, null);
        }
        switch (r) {
            case "int", "short", "byte", "long", "char", "boolean", "float", "double" -> {
                return primitive(r, isNat, isUnbounded);
            }
            default -> { }
        }
        int lt = r.indexOf('<');
        String noArgs = lt >= 0 ? r.substring(0, lt) : r;
        int lastDot = noArgs.lastIndexOf('.');
        String simple = lastDot >= 0 ? noArgs.substring(lastDot + 1) : noArgs;
        return new TypeRef(Kind.CLASS, r, noArgs, simple, lastDot >= 0, isNat, isUnbounded, null);
    }

    private static TypeRef primitive(String name, boolean isNat, boolean isUnbounded) {
        String display = (isNat ? "@Nat " : "") + (isUnbounded ? "@Unbounded " : "") + name;
        return new TypeRef(Kind.PRIMITIVE, display, null, name, false, isNat, isUnbounded, null);
    }

    // --- Laurel translation (mirror of the driver's translateType) ---

    /** JFE's integerType: @Unbounded -> unbounded int (or nat), else the fixed-width sorts. */
    private HighType integerType(String bounded, String boundedNat) {
        if (isUnbounded) {
            return isNat ? new HighType.UserDefined(LaurelFactory.ident("nat")) : new HighType.TInt();
        }
        return new HighType.UserDefined(LaurelFactory.ident(isNat ? boundedNat : bounded));
    }

    /**
     * The Laurel type of this reference, with JFE's exact decisions and
     * refusal messages: primitives map to the constrained
     * integer sorts / TBool / char; a type variable in scope refuses as
     * JFE's "Unsupported type: T"; any class reference becomes an opaque
     * composite sort — program classes qualified to their fqn, library
     * classes keeping the shortened simple name; a one-dimensional primitive
     * array becomes {@code JArray<E>} while the array model is enabled
     * ({@link ArrayModel#laurelType}); everything else (other arrays,
     * float/double, void) refuses with its display rendering.
     */
    HighType toLaurel(java.util.Set<String> typeParamsInScope,
                      ClassResolver resolver, CompositeModel composites) {
        return toLaurel(typeParamsInScope, java.util.Set.of(), resolver, composites);
    }

    /**
     * As above, but {@code tvarNames} are the type parameters that may become a
     * Laurel {@link HighType.TVar} instead of refusing -- the METHOD's own
     * parameters, which the procedure declares in its {@code typeArgs}, and in
     * a JArray member the class's parameter, which the {@code JArray}
     * composite declares. A type parameter in scope but NOT in
     * {@code tvarNames} (any other class type parameter) still refuses,
     * because its composite carries no {@code typeArgs} and the variable would
     * not resolve.
     */
    HighType toLaurel(java.util.Set<String> typeParamsInScope,
                      java.util.Set<String> tvarNames,
                      ClassResolver resolver, CompositeModel composites) {
        if (kind == Kind.PRIMITIVE) {
            return switch (simpleName) {
                case "int" -> integerType("int32", "nat31");
                case "short" -> integerType("int16", "nat15");
                case "byte" -> integerType("int8", "nat7");
                case "long" -> integerType("int64", "nat63");
                case "char" -> new HighType.UserDefined(LaurelFactory.ident("char"));
                case "boolean" -> new HighType.TBool();
                default -> throw new JavaViolationException("Unsupported type: " + display);
            };
        }
        if (kind == Kind.CLASS) {
            // A type variable, recovered from the Signature attribute. One the
            // method itself declares becomes a Laurel TVar; a class type
            // parameter refuses exactly as JFE's TypeVar arm does.
            if (!scoped && typeParamsInScope.contains(simpleName)) {
                if (tvarNames.contains(simpleName)) {
                    return new HighType.TVar(LaurelFactory.ident(simpleName));
                }
                throw new JavaViolationException("Unsupported type: " + simpleName);
            }
            HighType jarray = contractJArray(typeParamsInScope, tvarNames, resolver, composites);
            if (jarray != null) {
                return jarray;
            }
            String sortName = resolver.qualifyTypeName(nameWithScope);
            composites.referencedCompositeTypes.add(sortName);
            return new HighType.UserDefined(LaurelFactory.ident(sortName));
        }
        if (kind == Kind.ARRAY && composites.arrays.enabled()) {
            // A one-dimensional primitive array is the generic JArray<E>;
            // every other array type refuses with the model's reason.
            return composites.arrays.laurelType(this);
        }
        throw new JavaViolationException("Unsupported type: " + display);
    }

    /**
     * Contract code's generic {@code JArray<X>} (the built-in JArray class,
     * aliased to the {@code JArray} composite while library members
     * translate) as {@code Applied(JArray, [X])}, with {@code X} translated in
     * the same scope; null for any other reference. A raw or wildcard
     * {@code JArray} refuses.
     */
    private HighType contractJArray(java.util.Set<String> typeParamsInScope, java.util.Set<String> tvarNames,
                                    ClassResolver resolver, CompositeModel composites) {
        if (scoped || !ArrayModel.SORT.equals(nameWithScope)
                || !ArrayModel.SORT.equals(resolver.contractAliases.get(ArrayModel.SORT))
                || (fqnFromContext != null && !fqnFromContext.equals(JavaNames.dottedFqn(ArrayModel.JARRAY_INTERNAL)))
                || !composites.arrays.enabled()) {
            return null;
        }
        int lt = display.indexOf('<');
        String arg = lt >= 0 && display.endsWith(">") ? display.substring(lt + 1, display.length() - 1).strip() : "";
        if (arg.isEmpty() || arg.startsWith("?") || arg.indexOf(',') >= 0) {
            throw new JavaViolationException("Unsupported type: " + display);
        }
        HighType element = ofRendered(arg, false, false).toLaurel(typeParamsInScope, tvarNames, resolver, composites);
        return composites.arrays.jarrayOf(LaurelFactory.node(element));
    }
}
