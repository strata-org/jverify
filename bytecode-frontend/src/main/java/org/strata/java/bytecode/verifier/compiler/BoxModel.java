package org.strata.java.bytecode.verifier.compiler;

import java.util.List;

/**
 * The JDK's wrapper classes {@code Integer}, {@code Long}, {@code Short},
 * {@code Byte}, {@code Character} and {@code Boolean}, which their library
 * contracts describe (one field holding the primitive, a constructor,
 * {@code valueOf}, the unboxing method and {@code equals}). What is here is
 * what the contracts do not say: which classes are wrappers, with their
 * primitive and unboxing method, the Java supertypes a box flows into, and
 * when a value may be a box whose identity or nullness Java decides
 * ({@link #hazard}). A Laurel value of a composite type is never null and
 * {@code valueOf} allocates where Java may return a cached box, so a box
 * that may be null and a reference comparison between boxes are refused
 * where they are used (InvokeTranslator, BodyTranslator).
 */
final class BoxModel {

    /** A wrapper class: its internal name, primitive descriptor and unboxing method. */
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

    BoxModel(LibraryContracts contracts, ClassResolver resolver, CompositeModel composites) {
        this.contracts = contracts;
        this.resolver = resolver;
        this.composites = composites;
    }

    /**
     * Whether {@code w} names the JDK's wrapper class here: no program class has its name and no
     * program {@code @Contract} class describes it.
     */
    boolean hazard(Wrapper w) {
        return w != null
                && !resolver.knownClasses.containsKey(w.simpleName())
                && !resolver.programClassModels.containsKey(w.fqn())
                && resolver.libraryContractOf(w.fqn()) == null;
    }

    /** The composite sort of {@code w}, as every use of the type names it ({@link TypeRef#toLaurel}). */
    private String sortOf(Wrapper w) {
        return resolver.qualifyTypeName(w.simpleName(), w.fqn());
    }

    /**
     * The supertype sorts the composite {@code sort} extends when it is a
     * JDK wrapper class, modelled here or by a library contract, or null when
     * it is not one. These are the wrapper's Java
     * supertypes ({@code Number}, {@code Comparable}, {@code Serializable},
     * {@code Constable}, ...) that the program references, so a box flows into
     * a parameter, local, field or return of any of those types. Each is
     * extended directly, because a library sort is emitted with no supertypes
     * of its own. An empty list leaves the {@code Object} parent
     * (CompositeModel.emitCompositeClosure).
     */
    List<String> supertypeSorts(String sort) {
        for (var w : Wrapper.values()) {
            if (!hazard(w) || !sortOf(w).equals(sort)) {
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
}
