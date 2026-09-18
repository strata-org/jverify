package org.strata.java.bytecode.verifier.compiler;


import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.TypeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Laurel composite-sort registry: which class-typed sorts the program
 * references, their accessed fields, extends links, and instance procedures,
 * plus the closure emission that declares every sort with its full Java
 * supertype chain (ancestors first, as Laurel requires for scope copying).
 *
 * <p>Extracted verbatim from {@code BytecodeToLaurelCompiler}.
 */
final class CompositeModel {

    /** The Laurel sort name of {@code java.lang.Object} (JFE's OBJECT_SORT). */
    static final String OBJECT_SORT = "Object";

    /** Composite sorts referenced anywhere (JFE's referencedCompositeTypes). */
    final java.util.LinkedHashSet<String> referencedCompositeTypes = new java.util.LinkedHashSet<>();
    /** Accessed fields per composite (JFE's compositeFields): only accessed fields are declared. */
    final Map<String, LinkedHashMap<String, org.strata.jverify.laurel.Field>> compositeFields = new LinkedHashMap<>();
    /** Composite sort -> its extended (parent) sort (JFE's compositeExtends). */
    final Map<String, String> compositeExtends = new LinkedHashMap<>();
    /** Instance procedures per composite sort (JFE's instanceProcsBySort). */
    final Map<String, List<Procedure>> instanceProcsBySort = new LinkedHashMap<>();

    /** One declared field: descriptor/signature-sourced type + finality. */
    record FieldInfo(TypeRef type, boolean isFinal) {
    }

    /** Declared field types per program class: fqn -> field name -> info. */
    final Map<String, Map<String, FieldInfo>> declaredFields = new HashMap<>();

    private final ClassResolver resolver;

    CompositeModel(ClassResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Record that composite {@code child} extends {@code parent}, declaring
     * both sorts. Membership in referencedCompositeTypes and the edge in
     * compositeExtends always move together, so a linked child cannot
     * silently miss its declaration; a conflicting parent edge is refused
     * loudly (JFE's linkExtends).
     */
    void linkExtends(String childSort, String parentSort) {
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
    void registerHierarchyUpToThrowable(String fqn) {
        String stop = fqn;
        for (String c = fqn; c != null; c = resolver.superclassOf(c)) {
            stop = c;
            if (c.equals("java.lang.Throwable")) {
                break;
            }
        }
        for (String c = fqn; c != null && !c.equals(stop); c = resolver.superclassOf(c)) {
            String parent = resolver.superclassOf(c);
            if (parent == null) {
                break;
            }
            linkExtends(c, parent);
        }
        referencedCompositeTypes.add(stop);
    }

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
    void emitCompositeClosure(String sortName, java.util.Set<String> emitted, List<TypeDefinition> out) {
        if (!emitted.add(sortName)) {
            return;
        }
        List<AstNode<HighType>> extending = new ArrayList<>();
        for (String parent : directSupertypeSorts(sortName)) {
            emitCompositeClosure(parent, emitted, out);
            extending.add(LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(parent))));
        }
        if (extending.isEmpty() && !OBJECT_SORT.equals(sortName)) {
            emitCompositeClosure(OBJECT_SORT, emitted, out);
            extending.add(LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(OBJECT_SORT))));
        }
        List<org.strata.jverify.laurel.Field> fields = compositeFields.containsKey(sortName)
                ? new ArrayList<>(compositeFields.get(sortName).values())
                : List.of();
        out.add(new TypeDefinition.Composite(new org.strata.jverify.laurel.CompositeType(
                LaurelFactory.ident(sortName), /*typeArgs=*/List.of(), extending, fields,
                instanceProcsBySort.getOrDefault(sortName, List.of()))));
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
        var out = new ArrayList<String>();
        // An interface has no superclass edge: bytecode records Object as its
        // super_class, but JLS 4.10.2 makes Object a DIRECT supertype only of a
        // superinterface-less interface (handled by the Object fallback in
        // emitCompositeClosure), matching JFE's getSuperclass()==noType.
        String sup = resolver.isInterface(sortName) ? null : resolver.superclassOf(sortName);
        if (sup != null) {
            out.add(sortNameForFqn(sup));
        }
        for (String iface : resolver.directInterfacesOf(sortName)) {
            String s = sortNameForFqn(iface);
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /** The composite sort name for an fqn: {@code Object} for java.lang.Object, else the dotted fqn. */
    static String sortNameForFqn(String fqn) {
        return fqn.equals("java.lang.Object") ? OBJECT_SORT : fqn.replace('$', '.');
    }
}
