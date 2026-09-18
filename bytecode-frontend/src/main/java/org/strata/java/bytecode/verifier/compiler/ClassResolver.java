package org.strata.java.bytecode.verifier.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The type universe: every class this compilation knows about (program
 * classfiles, javac-classpath classfiles, and JCL/library classes reached
 * reflectively), with name resolution, superclass/interface walks, and the
 * classfile-driven policy gates (opt-out, overload, dispatch).
 *
 * <p>Extracted verbatim from {@code BytecodeToLaurelCompiler}.
 */
final class ClassResolver {

    /** simple or fqn class name -> fqn ('$'->'.'), for resolving static call owners. */
    final Map<String, String> knownClasses = new HashMap<>();
    /** fqn -> direct superclass fqn, from the classfiles of this compilation. */
    final Map<String, String> programSuper = new HashMap<>();
    /** Classfile models of this compilation by fqn ('$'->'.'), for the dispatch/overload gates. */
    final Map<String, java.lang.classfile.ClassModel> programClassModels = new HashMap<>();
    /** Per-file single-type imports of the unit under compilation: simple name -> fqn. */
    Map<String, String> imports = Map.of();

    /** javac classpath entries, for inventorying library contractees' methods. */
    private final List<java.nio.file.Path> classPath;
    private final boolean verifyByDefault;

    ClassResolver(List<java.nio.file.Path> classPath, boolean verifyByDefault) {
        this.classPath = classPath;
        this.verifyByDefault = verifyByDefault;
    }

    // --- Name resolution ---

    /**
     * Resolve a source-form class reference to an fqn: this compilation's
     * classes first, then the unit's single-type imports, then an already-
     * qualified name as-is, then {@code java.lang} (whose types appear
     * unqualified, never imported).
     */
    String resolveClassFqn(String name) {
        String qualified = qualifyTypeName(name);
        if (knownClasses.containsKey(qualified) || qualified.indexOf('.') >= 0) {
            return qualified;
        }
        String imported = imports.get(qualified);
        if (imported != null) {
            return imported;
        }
        try {
            Class.forName("java.lang." + qualified, false, getClass().getClassLoader());
            return "java.lang." + qualified;
        } catch (ClassNotFoundException e) {
            return qualified;
        }
    }

    /** Fully qualify a source-form type name against this compilation's classes (JFE prints fqns). */
    String qualifyTypeName(String name) {
        if (name.indexOf('.') >= 0) {
            // Nested reference like Outer.Inner: qualify via the outer class.
            String outer = name.substring(0, name.indexOf('.'));
            String fqnOuter = knownClasses.get(outer);
            return fqnOuter != null ? fqnOuter + name.substring(name.indexOf('.')) : name;
        }
        return knownClasses.getOrDefault(name, name);
    }

    // --- Classfile / reflective loading ---

    /** Load a classfile from the javac classpath (dirs and jars), or null. */
    java.lang.classfile.ClassModel loadClassModel(String fqn) {
        var direct = loadClassModelExact(fqn);
        if (direct != null) {
            return direct;
        }
        // Nested classfile: logical Outer.Inner is Outer$Inner on disk. Try
        // '$' at each dot position from the right.
        int dot = fqn.lastIndexOf('.');
        while (dot > 0) {
            String binary = fqn.substring(0, dot) + "$" + fqn.substring(dot + 1).replace('.', '$');
            var cm = loadClassModelExact(binary);
            if (cm != null) {
                return cm;
            }
            dot = fqn.lastIndexOf('.', dot - 1);
        }
        return null;
    }

    private java.lang.classfile.ClassModel loadClassModelExact(String binaryFqn) {
        String rel = binaryFqn.replace('.', '/') + ".class";
        for (var entry : classPath) {
            try {
                if (java.nio.file.Files.isDirectory(entry)) {
                    var p = entry.resolve(rel);
                    if (java.nio.file.Files.exists(p)) {
                        return java.lang.classfile.ClassFile.of().parse(p);
                    }
                } else if (entry.toString().endsWith(".jar") && java.nio.file.Files.exists(entry)) {
                    try (var jar = new java.util.jar.JarFile(entry.toFile())) {
                        var je = jar.getJarEntry(rel);
                        if (je != null) {
                            return java.lang.classfile.ClassFile.of().parse(jar.getInputStream(je).readAllBytes());
                        }
                    }
                }
            } catch (java.io.IOException | RuntimeException ignore) {
                // fall through to the next entry
            }
        }
        return null;
    }

    /**
     * Reflectively load {@code dottedName}, which is a composite SORT name: its
     * nested-class separators were flattened from {@code $} to {@code .} (by
     * {@code sortNameForFqn} and the {@code asInternalName().replace('$','.')}
     * used when recording interfaces). {@code Class.forName} needs the binary
     * name with {@code $} for a nested class, and a dotted sort name is
     * ambiguous (package dots vs. nesting dots are indistinguishable), so mirror
     * {@link #loadClassModel}: try the name as-is, then progressively rewrite
     * trailing {@code .} to {@code $} until it loads. Returns null if none do.
     */
    Class<?> loadClassReflectively(String dottedName) {
        Class<?> c = tryLoadClass(dottedName);
        if (c != null) {
            return c;
        }
        for (int dot = dottedName.lastIndexOf('.'); dot > 0; dot = dottedName.lastIndexOf('.', dot - 1)) {
            String binary = dottedName.substring(0, dot) + "$" + dottedName.substring(dot + 1).replace('.', '$');
            c = tryLoadClass(binary);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * The loaded Class for {@code fqn}, or null when it is genuinely absent
     * (initialization suppressed). A {@link LinkageError} -- a class that is
     * found but cannot be linked, e.g. a missing transitive dependency -- is
     * NOT swallowed: it propagates so a misconfigured classpath fails loudly
     * instead of silently degrading a composite sort to {@code extends Object}.
     */
    static Class<?> tryLoadClass(String fqn) {
        try {
            return Class.forName(fqn, false, BytecodeToLaurelCompiler.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // --- Hierarchy walks ---

    /** Direct interface fqns of {@code fqn} (classfile if a program type, else reflection). */
    List<String> directInterfacesOf(String fqn) {
        var out = new java.util.ArrayList<String>();
        var model = programClassModels.get(fqn);
        if (model != null) {
            for (var iface : model.interfaces()) {
                out.add(JavaNames.dottedFqn(iface.asInternalName()));
            }
            return out;
        }
        Class<?> c = loadClassReflectively(fqn);
        if (c != null) {
            for (Class<?> i : c.getInterfaces()) {
                out.add(i.getName().replace('$', '.'));
            }
        }
        return out;
    }

    /** True iff {@code fqn} is an interface (classfile flags if a program type, else reflection). */
    boolean isInterface(String fqn) {
        var model = programClassModels.get(fqn);
        if (model != null) {
            return (model.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_INTERFACE) != 0;
        }
        Class<?> c = loadClassReflectively(fqn);
        return c != null && c.isInterface();
    }

    /**
     * The direct superclass fqn of {@code fqn}, or null at the top of the
     * chain (or for an unresolvable class). Program classes resolve from
     * their classfiles; everything else (JCL, libraries on the verifier's own
     * classpath) via reflection without initialization.
     */
    String superclassOf(String fqn) {
        String prog = programSuper.get(fqn);
        if (prog != null) {
            return prog;
        }
        Class<?> c = loadClassReflectively(fqn);
        if (c == null) {
            return null;
        }
        Class<?> s = c.getSuperclass();
        return s == null ? null : s.getName().replace('$', '.');
    }

    /** True iff {@code fqn} is {@code ancestor} or below it on the superclass chain. */
    boolean isSubclassOf(String fqn, String ancestor) {
        for (String c = fqn; c != null; c = superclassOf(c)) {
            if (c.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** True iff {@code fqn} has {@code java.lang.Throwable} on its superclass chain. */
    boolean isThrowableSubtype(String fqn) {
        return isSubclassOf(fqn, "java.lang.Throwable");
    }

    /**
     * Least common ancestor of two classes along their superclass chains.
     * Interfaces are irrelevant: thrown types are classes with single
     * inheritance. Terminates at worst at {@code java.lang.Object}.
     */
    String leastCommonAncestor(String a, String b) {
        var bChain = new java.util.LinkedHashSet<String>();
        for (String c = b; c != null; c = superclassOf(c)) {
            bChain.add(c);
        }
        for (String c = a; c != null; c = superclassOf(c)) {
            if (bChain.contains(c)) {
                return c;
            }
        }
        throw new JavaViolationException("thrown types share no common ancestor");
    }

    // --- Callee lookup + classfile-driven policy gates ---

    /** A located callee: the classfile method plus the class that declares it. */
    record Callee(String declaringFqn, java.lang.classfile.MethodModel method) {
    }

    /**
     * Find the (non-synthetic) method {@code name} on {@code receiverFqn} or
     * its program-class ancestry, ancestor-nearest-first from the receiver.
     */
    Callee findCallee(String receiverFqn, String name) {
        for (String fqn = receiverFqn; fqn != null; fqn = programSuper.get(fqn)) {
            var cm = programClassModels.get(fqn);
            if (cm == null) {
                break;
            }
            for (var m : cm.methods()) {
                if (m.methodName().stringValue().equals(name)
                        && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE
                                | java.lang.classfile.ClassFile.ACC_STATIC)) == 0) {
                    return new Callee(fqn, m);
                }
            }
        }
        return null;
    }

    /** The {@code @Verify} annotation on an element's RuntimeInvisibleAnnotations, or null. */
    static java.lang.classfile.Annotation verifyAnnotation(
            java.lang.classfile.AttributedElement element) {
        var annos = element
                .findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .map(java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute::annotations)
                .orElse(List.of());
        for (var a : annos) {
            if (a.className().stringValue().equals("Lorg/strata/jverify/Verify;")) {
                return a;
            }
        }
        return null;
    }

    static boolean verifyValue(java.lang.classfile.Annotation verify, String element,
                               boolean defaultValue) {
        for (var el : verify.elements()) {
            if (el.name().stringValue().equals(element)
                    && el.value() instanceof java.lang.classfile.AnnotationValue.OfBoolean b) {
                return b.booleanValue();
            }
        }
        return defaultValue;
    }

    /**
     * JFE's resolved (stacked) opt-out status for a classfile method: the
     * method's own {@code @Verify} wins unless the class's says
     * {@code overrideChildren}; with neither, the class-level value, then
     * verify-by-default, decides. Readable from the classfile because the
     * spec library compiled against is the CLASS-retention variant.
     */
    boolean isOptedOut(java.lang.classfile.ClassModel cm, java.lang.classfile.MethodModel m) {
        var classAnno = verifyAnnotation(cm);
        var methodAnno = verifyAnnotation(m);
        if (classAnno != null && verifyValue(classAnno, "overrideChildren", false)) {
            return !verifyValue(classAnno, "value", true);
        }
        if (methodAnno != null) {
            return !verifyValue(methodAnno, "value", true);
        }
        if (classAnno != null) {
            return !verifyValue(classAnno, "value", true);
        }
        return !verifyByDefault;
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
    void refuseIfOverloaded(String classFqn, String name, boolean isStatic) {
        var cm = programClassModels.get(classFqn);
        if (cm == null) {
            return;
        }
        long count = cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .filter(m -> (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                        | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0)
                .filter(m -> ((m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) != 0)
                        == isStatic)
                .filter(m -> !isOptedOut(cm, m))
                .count();
        if (count > 1) {
            throw new JavaViolationException("overloaded method '" + name
                    + "' (enclosing class declares " + count + " methods with this name); "
                    + "overloading is not supported");
        }
    }

    /**
     * JFE's dispatchRefusal: an instance call is translatable only when
     * provably monomorphic -- the callee is final or private, or its
     * declaring class is final, or the receiver's static type is a final
     * class. Deliberately not keyed on whether the callee overrides
     * something: the unsound case is a non-overriding method overridden by a
     * subtype later.
     */
    void enforceDispatchGate(String receiverFqn, Callee callee, String name) {
        int mflags = callee.method().flags().flagsMask();
        if ((mflags & (java.lang.classfile.ClassFile.ACC_FINAL
                | java.lang.classfile.ClassFile.ACC_PRIVATE)) != 0) {
            return;
        }
        var declaring = programClassModels.get(callee.declaringFqn());
        if (declaring != null
                && (declaring.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_FINAL) != 0) {
            return;
        }
        var receiver = programClassModels.get(receiverFqn);
        if (receiver != null
                && (receiver.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_FINAL) != 0) {
            return;
        }
        throw new JavaViolationException(
                "polymorphic dispatch through interface/superclass is not yet supported");
    }

    /** Canonical erased-parameter signature of a classfile method (JVM descriptors). */
    private static String paramDescriptors(java.lang.classfile.MethodModel m) {
        return m.methodTypeSymbol().parameterList().stream()
                .map(java.lang.constant.ClassDesc::descriptorString)
                .toList().toString();
    }

    /** Canonical erased-parameter signature of a reflected method (same format). */
    private static String paramDescriptors(java.lang.reflect.Method m) {
        return java.util.Arrays.stream(m.getParameterTypes())
                .map(Class::descriptorString)
                .toList().toString();
    }

    /**
     * JFE's ancestryOverloadRefusal: by-name resolution must be unambiguous
     * over the receiver's supertype closure. Counts distinct erased
     * parameter signatures of {@code name}, skipping synthetic/bridge members
     * and private members of strict supertypes (JLS 8.2: not members of the
     * receiver's type). More than one signature refuses; same-signature
     * overRIDES are fine.
     */
    void enforceAncestryOverloadGate(String receiverFqn, String name) {
        var signatures = new java.util.LinkedHashSet<String>();
        boolean beyondReceiver = false;
        boolean anyBeyond = false;
        // Program-class portion: the classfile models of this compilation.
        String fqn = receiverFqn;
        while (fqn != null && programClassModels.containsKey(fqn)) {
            var cm = programClassModels.get(fqn);
            for (var m : cm.methods()) {
                if (!m.methodName().stringValue().equals(name)
                        || (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE
                                | java.lang.classfile.ClassFile.ACC_STATIC)) != 0) {
                    continue;
                }
                if (beyondReceiver
                        && (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_PRIVATE) != 0) {
                    continue;
                }
                if (isOptedOut(cm, m)) {
                    continue; // emits no procedure, so it cannot collide
                }
                if (signatures.add(paramDescriptors(m)) && beyondReceiver) {
                    anyBeyond = true;
                }
            }
            beyondReceiver = true;
            fqn = cm.superclass()
                    .map(sup -> JavaNames.dottedFqn(sup.asInternalName()))
                    .orElse(null);
        }
        // JCL/library ancestry via REFLECTION. Reading these classfiles as
        // classpath resources is not portable (the JRT does not serve them
        // as resources on every runtime -- it works on the dev desktop but
        // not the build fleet); the loaded Class is always available. JFE
        // uses javacTypes.closure() here, e.g. Object.toString() colliding
        // with a user toString(x).
        if (fqn != null) {
            // The fqn is dotted-flattened, so a nested library type needs the
            // progressive .->$ rewrite of loadClassReflectively to load.
            for (Class<?> c = loadClassReflectively(fqn); c != null; c = c.getSuperclass()) {
                for (var m : c.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (!m.getName().equals(name) || m.isSynthetic() || m.isBridge()
                            || java.lang.reflect.Modifier.isStatic(mod)) {
                        continue;
                    }
                    if (beyondReceiver && java.lang.reflect.Modifier.isPrivate(mod)) {
                        continue;
                    }
                    if (signatures.add(paramDescriptors(m)) && beyondReceiver) {
                        anyBeyond = true;
                    }
                }
                beyondReceiver = true;
            }
        }
        if (signatures.size() > 1) {
            String where = anyBeyond
                    ? "'" + receiverFqn + "' and its supertypes"
                    : "'" + receiverFqn + "'";
            throw new JavaViolationException("overloaded method '" + name + "' ("
                    + signatures.size() + " signatures visible on " + where
                    + "); overloading is not supported");
        }
    }
}
