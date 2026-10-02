package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BytecodeFrontend.ClassSource;
import org.strata.java.bytecode.frontend.ClassfileQueries;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The program's type declarations, recovered from the classfiles: the
 * re-nested {@link TypeNode} tree of every compilation unit, the source-order
 * method sets, and the @Contract/@Pure markers the later passes consume.
 */
final class Declarations {

    /** One re-nested program type, members in InnerClasses-attribute order. */
    record TypeNode(String fqn, String name, java.lang.classfile.ClassModel cm,
                    boolean anonymous, List<TypeNode> nested) {
    }

    /** One @Contract-annotated class declaration awaiting validation. */
    record ContractDecl(String fqn, TypeNode decl, URI uri) {
    }

    /** All type declarations of this compilation by fqn. */
    final Map<String, TypeNode> programTypes = new LinkedHashMap<>();
    /** fqns of @Contract-annotated classes: consumed as contract definitions, not user code. */
    final Set<String> contractClasses = new HashSet<>();
    /**
     * SOURCE contracts to relocate, contractee fqn -> its @Contract class: JFE's
     * ExternalContractCompiler.handleSourceContract. Each bodyless method of the
     * contractee is translated from the same-signature method of the contract class,
     * and the contract class's fields become the contractee's. Only well-formed
     * contracts are entered (see ContractValidator), mirroring JFE skipping a
     * contract class whose declaration it already rejected.
     */
    final Map<String, TypeNode> sourceContracts = new HashMap<>();
    /**
     * LIBRARY contracts, contractee fqn -> its @Contract class: JFE's
     * handleLibraryContract, which makes the contract class stand for the library
     * type, so a call on a receiver of that type binds to the contract's method.
     */
    final Map<String, ContractDecl> libraryContracts = new LinkedHashMap<>();
    /** @Contract declarations in compilation order. */
    final List<ContractDecl> contractDecls = new ArrayList<>();
    /**
     * {@link #purityKey} -> is annotated @Pure (for the heap-write fallback). Keyed
     * per OVERLOAD: keyed by name alone, same-named siblings overwrote each other and
     * an impure overload could read as pure, so its caller lost the heap frame it
     * needs (OverloadPurityKey).
     */
    private final Map<String, Boolean> pureMethods = new HashMap<>();

    /** The per-overload key of {@code m} declared in {@code fqn}: its name plus its erased descriptor. */
    static String purityKey(String fqn, java.lang.classfile.MethodModel m) {
        return fqn + "_" + m.methodName().stringValue() + m.methodTypeSymbol().descriptorString();
    }

    /** True iff the method behind {@link #purityKey} is annotated @Pure. */
    boolean isPure(String key) {
        return pureMethods.getOrDefault(key, false);
    }

    /** Record {@code node}'s tree: types, @Contract markers, @Pure markers. */
    void collect(TypeNode node, URI uri) {
        programTypes.put(node.fqn(), node);
        if (hasAnnotation(node.cm(), "Contract")) {
            contractClasses.add(node.fqn());
            contractDecls.add(new ContractDecl(node.fqn(), node, uri));
        }
        for (var m : declaredMethodsOf(node.cm())) {
            pureMethods.put(purityKey(node.fqn(), m), hasAnnotation(m, "Pure"));
        }
        for (var child : node.nested()) {
            collect(child, uri);
        }
    }

    // --- The type tree ---

    static TypeNode typeTreeOf(ClassSource cls) {
        var pool = new HashMap<String, java.lang.classfile.ClassModel>();
        pool.put(cls.classModel().thisClass().asInternalName(), cls.classModel());
        for (var n : cls.nestedModels()) {
            pool.put(n.thisClass().asInternalName(), n);
        }
        String fqn = JavaNames.dottedFqn(cls.internalName());
        return typeNode(fqn, JavaNames.simpleName(fqn), cls.classModel(), false, pool);
    }

    private static TypeNode typeNode(String fqn, String name, java.lang.classfile.ClassModel cm,
                                     boolean anonymous, Map<String, java.lang.classfile.ClassModel> pool) {
        String self = cm.thisClass().asInternalName();
        // An enum constant's body subclass is consumed by the constant
        // declaration, never a nested member.
        Set<String> exclude = cm.flags().has(java.lang.reflect.AccessFlag.ENUM)
                ? enumBodyClasses(cm, pool)
                : Set.of();
        var nested = new ArrayList<TypeNode>();
        var counts = new HashMap<String, Integer>();
        for (var ic : ClassfileQueries.innerClassesOf(cm)) {
            String inner = ic.innerClass().asInternalName();
            if (inner.equals(self) || exclude.contains(inner)) {
                continue;
            }
            var nestedCm = pool.get(inner);
            if (nestedCm == null) {
                continue;
            }
            if (!self.equals(ClassfileQueries.directOuter(nestedCm, ic))) {
                continue;
            }
            boolean anon = ic.innerName().isEmpty();
            String childName = ic.innerName().map(u -> u.stringValue())
                    .orElseGet(() -> ClassfileQueries.simple(inner));
            // Local classes hoisted from different methods may share a name
            // (disambiguated by occurrence, as javac's flat names are).
            int occurrence = counts.merge(childName, 1, Integer::sum);
            String childFqn = ic.innerName().isEmpty() || ic.outerClass().isEmpty()
                    ? JavaNames.dottedFqn(inner)
                    : fqn + "." + childName + (occurrence > 1 ? "$" + occurrence : "");
            nested.add(typeNode(childFqn, childName, nestedCm, anon, pool));
        }
        return new TypeNode(fqn, name, cm, anonymous, nested);
    }

    /** Anonymous inner classes extending this enum: the constant bodies. */
    private static Set<String> enumBodyClasses(java.lang.classfile.ClassModel cm,
            Map<String, java.lang.classfile.ClassModel> pool) {
        String self = cm.thisClass().asInternalName();
        var out = new HashSet<String>();
        for (var ic : ClassfileQueries.innerClassesOf(cm)) {
            if (ic.innerName().isPresent()) {
                continue;
            }
            var nested = pool.get(ic.innerClass().asInternalName());
            if (nested != null && nested.superclass()
                    .map(sc -> sc.asInternalName().equals(self)).orElse(false)) {
                out.add(ic.innerClass().asInternalName());
            }
        }
        return out;
    }

    // --- The source-declared method set ---

    /**
     * The classfile methods that correspond to SOURCE method declarations,
     * kind-for-kind (class/enum/record/annotation rules): the method walk
     * set and the overload-ordinal universe.
     */
    static List<java.lang.classfile.MethodModel> declaredMethodsOf(
            java.lang.classfile.ClassModel cm) {
        var out = new ArrayList<java.lang.classfile.MethodModel>();
        var flags = cm.flags();
        if (flags.has(java.lang.reflect.AccessFlag.ANNOTATION)) {
            return out;   // elements were AnnotationMemberDeclarations, never compiled
        }
        boolean isEnum = flags.has(java.lang.reflect.AccessFlag.ENUM);
        var rec = cm.findAttribute(java.lang.classfile.Attributes.record()).orElse(null);
        Set<String> compNames = rec == null
                ? Set.of()
                : rec.components().stream().map(c -> c.name().stringValue())
                        .collect(java.util.stream.Collectors.toSet());
        for (var m : cm.methods()) {
            String n = m.methodName().stringValue();
            if (n.equals("<clinit>") || n.equals("<init>")) {
                continue;   // ctors were ConstructorDeclarations; clinit consumed
            }
            if (isEnum) {
                if (n.equals("$values") || n.equals("values") || n.equals("valueOf")) {
                    continue;
                }
            } else if (rec != null) {
                if (compNames.contains(n) && m.methodTypeSymbol().parameterCount() == 0) {
                    continue;   // synthetic component accessor
                }
                if ((n.equals("equals") || n.equals("hashCode") || n.equals("toString"))
                        && ClassfileQueries.usesObjectMethods(m)) {
                    continue;   // ObjectMethods-generated
                }
            } else if (ClassfileQueries.isBridge(m)) {
                continue;       // erasure shim / synthetic
            }
            out.add(m);
        }
        return out;
    }

    /** {@code m}'s position among same-named methods of {@code methods} (overload ordinal). */
    static int ordinalOf(List<java.lang.classfile.MethodModel> methods,
                         java.lang.classfile.MethodModel m) {
        int ordinal = 0;
        for (var x : methods) {
            if (x == m) {
                break;
            }
            if (x.methodName().stringValue().equals(m.methodName().stringValue())) {
                ordinal++;
            }
        }
        return ordinal;
    }

    // --- Annotation and signature queries ---

    /** An element carries the given simple-named annotation (visible or invisible). */
    static boolean hasAnnotation(java.lang.classfile.AttributedElement el, String simpleName) {
        return annotationOf(el, simpleName) != null;
    }

    /** The named annotation on an element (visible or invisible), or null. */
    static java.lang.classfile.Annotation annotationOf(
            java.lang.classfile.AttributedElement el, String simpleName) {
        var annos = new ArrayList<java.lang.classfile.Annotation>();
        el.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                .ifPresent(a -> annos.addAll(a.annotations()));
        el.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .ifPresent(a -> annos.addAll(a.annotations()));
        for (var a : annos) {
            if (java.lang.constant.ClassDesc.ofDescriptor(a.className().stringValue())
                    .displayName().equals(simpleName)) {
                return a;
            }
        }
        return null;
    }

    /** All annotation simple names on an element, source (= classfile) order. */
    static List<String> annotationNamesOf(java.lang.classfile.AttributedElement el) {
        var out = new ArrayList<String>();
        el.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                .ifPresent(a -> a.annotations().forEach(x -> out.add(
                        java.lang.constant.ClassDesc.ofDescriptor(x.className().stringValue())
                                .displayName())));
        el.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .ifPresent(a -> a.annotations().forEach(x -> out.add(
                        java.lang.constant.ClassDesc.ofDescriptor(x.className().stringValue())
                                .displayName())));
        return out;
    }

    /** The @Contract annotation's explicit target fqn (@Contract(Foo.class)), or null. */
    static String explicitContractTarget(java.lang.classfile.ClassModel cm) {
        var anno = annotationOf(cm, "Contract");
        if (anno == null) {
            return null;
        }
        for (var e : anno.elements()) {
            if (e.name().stringValue().equals("value")
                    && e.value() instanceof java.lang.classfile.AnnotationValue.OfClass oc) {
                return JavaNames.dottedFqn(oc.classSymbol().descriptorString()
                        .substring(1, oc.classSymbol().descriptorString().length() - 1));
            }
        }
        return null;
    }

    /** Type-parameter names from the class Signature attribute. */
    static List<String> typeParamNamesOf(java.lang.classfile.ClassModel cm) {
        var out = new ArrayList<String>();
        cm.findAttribute(java.lang.classfile.Attributes.signature()).ifPresent(sa -> {
            try {
                java.lang.classfile.ClassSignature.parseFrom(sa.signature().stringValue())
                        .typeParameters().forEach(tp -> out.add(tp.identifier()));
            } catch (RuntimeException ignore) {
                // absent/malformed: no type parameters
            }
        });
        return out;
    }

    /** The direct supertype fqns as source order gave them: superclass (if not Object) then interfaces. */
    static List<String> declaredSuperFqns(java.lang.classfile.ClassModel cm) {
        var out = new ArrayList<String>();
        cm.superclass().ifPresent(sc -> {
            String fqn = JavaNames.dottedFqn(sc.asInternalName());
            if (!fqn.equals("java.lang.Object")) {
                out.add(fqn);
            }
        });
        for (var itf : cm.interfaces()) {
            out.add(JavaNames.dottedFqn(itf.asInternalName()));
        }
        return out;
    }

    /** A plain concrete class: not an interface, not abstract, not enum/record/annotation. */
    static boolean isConcreteClass(java.lang.classfile.ClassModel cm) {
        var f = cm.flags();
        return !f.has(java.lang.reflect.AccessFlag.INTERFACE)
                && !f.has(java.lang.reflect.AccessFlag.ABSTRACT)
                && !f.has(java.lang.reflect.AccessFlag.ENUM)
                && !f.has(java.lang.reflect.AccessFlag.ANNOTATION)
                && cm.findAttribute(java.lang.classfile.Attributes.record()).isEmpty();
    }

    /** The method calls a JVerify header spec (an internally defined contract exists). */
    static boolean hasContractHeader(java.lang.classfile.MethodModel m) {
        var code = m.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var el : code) {
            if (el instanceof java.lang.classfile.instruction.InvokeInstruction inv
                    && inv.opcode() == java.lang.classfile.Opcode.INVOKESTATIC) {
                String owner = inv.owner().asInternalName();
                if (owner.endsWith("/JVerify") || owner.endsWith("/Verify")) {
                    switch (inv.name().stringValue()) {
                        case "precondition", "postcondition", "reads", "modifies" -> {
                            return true;
                        }
                        default -> {
                        }
                    }
                }
            }
        }
        return false;
    }

}
