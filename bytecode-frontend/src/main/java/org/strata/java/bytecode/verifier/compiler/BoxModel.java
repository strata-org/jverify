package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Condition;
import org.strata.jverify.laurel.ConditionMode;
import org.strata.jverify.laurel.CoroutineContracts;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.Parameter;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * The box model: the wrapper classes {@code Integer}, {@code Long},
 * {@code Short}, {@code Byte}, {@code Character} and {@code Boolean} as Laurel
 * composites with one immutable field, {@link #VALUE_FIELD}, holding the
 * primitive.
 *
 * <ul>
 *   <li>Boxing ({@code Integer.valueOf(int)}, javac's autoboxing, and
 *       {@code new Integer(int)}) calls the helper {@code java?box$Integer},
 *       which allocates the object and sets its value. Its contract states
 *       the value only: Java caches the boxes of small values, so a box is not
 *       guaranteed to be fresh.</li>
 *   <li>Unboxing ({@code intValue()} and its siblings) reads the field, or
 *       takes the boxed primitive itself when the box was made in the same
 *       expression ({@code Integer.valueOf(x).intValue()},
 *       {@code b.equals(x)}). A Laurel value of a composite type is never
 *       null, so where this model applies a box that may be null is
 *       refused: at a {@code null} literal, and where a box read from a field
 *       or array is unboxed or compared with {@code equals}
 *       (InvokeTranslator.boxMember). A box passed as a parameter or returned
 *       by a call is read as non-null, as every reference is. A wrapper class
 *       described by a library contract (see the last paragraph) is translated
 *       through the contract's members instead, whose receivers are read as
 *       non-null too, so none of these refusals applies to it.</li>
 *   <li>{@code equals(Object)} compares the values when the argument is a box
 *       of the same class, and is false for any other object.</li>
 *   <li>A cast to a box class is Laurel's {@code as} and an {@code instanceof}
 *       is Laurel's {@code is}, as for any composite (BodyTranslator.typeCheck).</li>
 * </ul>
 *
 * <p>A box inside a specification refuses that method, as any other
 * allocation there does (BodyTranslator.checkContractArg).
 *
 * <p>Reference comparison between boxes stays refused
 * ({@code BodyTranslator.refuseUnsoundReferenceEquality}): the cache makes
 * {@code ==} on two boxes of the same small value true, which fresh
 * allocations would not reproduce.
 *
 * <p>A wrapper class is modelled only while nothing else describes it: no
 * library contract (object-contract.java, the contract path, or a program
 * {@code @Contract} class) and no program class of the same simple name. A
 * contract keeps its own representation, which this model would contradict.
 */
final class BoxModel {

    /** The composite field holding a box's primitive value. */
    static final String VALUE_FIELD = "value";
    /** Name prefix of the boxing helper procedures, followed by the wrapper's simple name. */
    static final String BOX_HELPER_PREFIX = "java?box$";

    /** A modelled wrapper class: its internal name, primitive descriptor and unboxing method. */
    enum Wrapper {
        INTEGER("java/lang/Integer", "I", "intValue"),
        LONG("java/lang/Long", "J", "longValue"),
        SHORT("java/lang/Short", "S", "shortValue"),
        BYTE("java/lang/Byte", "B", "byteValue"),
        CHARACTER("java/lang/Character", "C", "charValue"),
        BOOLEAN("java/lang/Boolean", "Z", "booleanValue");

        final String internalName;
        final String primitiveDesc;
        final String unboxName;

        Wrapper(String internalName, String primitiveDesc, String unboxName) {
            this.internalName = internalName;
            this.primitiveDesc = primitiveDesc;
            this.unboxName = unboxName;
        }

        String fqn() {
            return JavaNames.dottedFqn(internalName);
        }

        String simpleName() {
            return JavaNames.simpleName(fqn());
        }

        String descriptor() {
            return "L" + internalName + ";";
        }
    }

    /** The wrapper class with this internal name, or null. */
    static Wrapper wrapperOf(String internalName) {
        for (var w : Wrapper.values()) {
            if (w.internalName.equals(internalName)) {
                return w;
            }
        }
        return null;
    }

    private final LibraryContracts contracts;
    private final ClassResolver resolver;
    private final CompositeModel composites;

    /** The boxing helpers, by name, in first-use order. */
    final LinkedHashMap<String, Procedure> helpers = new LinkedHashMap<>();

    BoxModel(LibraryContracts contracts, ClassResolver resolver, CompositeModel composites) {
        this.contracts = contracts;
        this.resolver = resolver;
        this.composites = composites;
    }

    /** True iff {@code w} is translated by this model (see the class comment). */
    boolean models(Wrapper w) {
        return w != null
                && !resolver.knownClasses.containsKey(w.simpleName())
                && !resolver.programClassModels.containsKey(w.fqn())
                && resolver.libraryContractOf(w.fqn()) == null
                && !contracts.hasContractFor(w.fqn());
    }

    /** The composite sort of {@code w}, as every use of the type names it ({@link TypeRef#toLaurel}). */
    private String sortOf(Wrapper w) {
        return resolver.qualifyTypeName(w.simpleName(), w.fqn());
    }

    /**
     * The supertype sorts the composite {@code sort} extends when it is a
     * modelled box, or null when it is not one. These are the wrapper's Java
     * supertypes ({@code Number}, {@code Comparable}, {@code Serializable},
     * {@code Constable}, ...) that the program references, so a box flows into
     * a parameter, local, field or return of any of those types. Each is
     * extended directly, because a library sort is emitted with no supertypes
     * of its own. An empty list leaves the {@code Object} parent
     * (CompositeModel.emitCompositeClosure).
     */
    List<String> supertypeSorts(String sort) {
        for (var w : Wrapper.values()) {
            if (!models(w) || !sortOf(w).equals(sort)) {
                continue;
            }
            var out = new java.util.ArrayList<String>();
            for (Class<?> c : javaSupertypes(w)) {
                String s = resolver.qualifyTypeName(c.getSimpleName(), c.getName());
                s = resolver.contracteeOf.getOrDefault(s, s);
                if (composites.referencedCompositeTypes.contains(s) && !s.equals(sort) && !out.contains(s)) {
                    out.add(s);
                }
            }
            return out;
        }
        return null;
    }

    /** Every Java supertype of {@code w}'s class except {@code Object}: superclasses, then interfaces. */
    private static List<Class<?>> javaSupertypes(Wrapper w) {
        Class<?> cls;
        try {
            cls = Class.forName(w.fqn(), false, BoxModel.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return List.of();
        }
        var out = new java.util.LinkedHashSet<Class<?>>();
        var todo = new java.util.ArrayDeque<Class<?>>();
        todo.add(cls);
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (c.getSuperclass() != null && c.getSuperclass() != Object.class && out.add(c.getSuperclass())) {
                todo.add(c.getSuperclass());
            }
            for (Class<?> i : c.getInterfaces()) {
                if (out.add(i)) {
                    todo.add(i);
                }
            }
        }
        return List.copyOf(out);
    }

    /** The Laurel type of {@code w}'s primitive. */
    private HighType primitiveType(Wrapper w) {
        return TypeRef.ofDescriptor(w.primitiveDesc).toLaurel(java.util.Set.of(), resolver, composites);
    }

    /**
     * The box of {@code value}: a call to {@code w}'s boxing helper, which is
     * registered together with the composite and its field.
     */
    StmtExpr box(Wrapper w, AstNode<StmtExpr> value) {
        String name = registerHelper(w);
        return LaurelFactory.staticCall(LaurelFactory.ident(name), List.of(value), List.of());
    }

    /** The primitive {@code receiver} boxes: a read of its value field. */
    StmtExpr unbox(Wrapper w, AstNode<StmtExpr> receiver) {
        declare(w);
        return new StmtExpr.Var(new Variable.Field(receiver, LaurelFactory.ident(VALUE_FIELD)));
    }

    /** Declare {@code w}'s composite and its value field; returns the composite's sort. */
    String declare(Wrapper w) {
        String sort = sortOf(w);
        composites.referencedCompositeTypes.add(sort);
        composites.compositeFields.computeIfAbsent(sort, k -> new LinkedHashMap<>())
                .putIfAbsent(VALUE_FIELD, new org.strata.jverify.laurel.Field(
                        LaurelFactory.ident(VALUE_FIELD), /*isMutable*/ false,
                        LaurelFactory.node(primitiveType(w)), Optional.empty()));
        return sort;
    }

    /**
     * Register, once, {@code java?box$<W>(x): <W>}: opaque, ensuring
     * {@code $result#value == x} and {@code $result is <W>}, with an empty
     * frame, implemented by allocating the box and setting its value.
     */
    private String registerHelper(Wrapper w) {
        String sort = declare(w);
        String name = BOX_HELPER_PREFIX + w.simpleName();
        if (helpers.containsKey(name)) {
            return name;
        }
        HighType boxType = new HighType.UserDefined(LaurelFactory.ident(sort));
        AstNode<StmtExpr> x = LaurelFactory.noRange(LaurelFactory.var_("x"));
        AstNode<StmtExpr> result = LaurelFactory.noRange(LaurelFactory.reservedVar_(LaurelFactory.LAUREL_RESULT_BINDING));
        var post = new Condition(LaurelFactory.noRange(LaurelFactory.primitiveOp(new Operation.Eq(),
                LaurelFactory.noRange(new StmtExpr.Var(new Variable.Field(result, LaurelFactory.ident(VALUE_FIELD)))),
                x)), Optional.empty(), new ConditionMode.Both());
        var isBox = new Condition(LaurelFactory.noRange(new StmtExpr.IsType(result, LaurelFactory.node(boxType))),
                Optional.empty(), new ConditionMode.Both());
        String b = "b";
        var declare = LaurelFactory.noRange(new StmtExpr.Assign(List.of(variable(new Variable.Declare(
                new ParameterOpt(LaurelFactory.ident(b), Optional.of(LaurelFactory.node(boxType)))))),
                LaurelFactory.noRange(new StmtExpr.New(LaurelFactory.ident(sort), List.of()))));
        var set = LaurelFactory.noRange(new StmtExpr.Assign(List.of(variable(new Variable.Field(
                LaurelFactory.noRange(LaurelFactory.var_(b)), LaurelFactory.ident(VALUE_FIELD)))), x));
        var ret = LaurelFactory.noRange(new StmtExpr.Return(List.of(LaurelFactory.noRange(LaurelFactory.var_(b)))));
        var implementation = LaurelFactory.noRange(
                new StmtExpr.Block(List.of(declare, set, ret), Optional.empty(), List.of(), Optional.empty()));
        helpers.put(name, new Procedure(LaurelFactory.ident(name), List.of(),
                List.of(new Parameter(LaurelFactory.ident("x"), LaurelFactory.node(primitiveType(w)))),
                List.of(new Parameter(LaurelFactory.reservedIdent(LaurelFactory.LAUREL_RESULT_BINDING),
                        LaurelFactory.node(boxType))),
                List.of(), new CoroutineContracts.Regular(), Optional.empty(),
                new Body.Opaque(List.of(post, isBox), Optional.of(implementation), LaurelFactory.unguardedModifies(List.of())),
                Optional.empty(), false, List.of(), Optional.empty(), Optional.empty(),
                /*throwsInferred=*/false, List.of(),
                /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of()));
        return name;
    }

    private static AstNode<Variable> variable(Variable v) {
        return new AstNode<>(v, SourceMap.UNKNOWN_RANGE);
    }
}
