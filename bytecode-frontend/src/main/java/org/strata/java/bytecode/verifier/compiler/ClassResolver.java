package org.strata.java.bytecode.verifier.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The type universe: every class this compilation knows about (program
 * classfiles, javac-classpath classfiles, what javac knew of the non-JDK
 * classes the program mentions, and JCL/library classes reached
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
    /**
     * Contract class printed name -> contractee sort, consulted first by
     * {@link #qualifyTypeName}; empty except while library members are translated.
     */
    Map<String, String> contractAliases = Map.of();

    /** javac classpath entries, for inventorying library contractees' methods. */
    private final List<java.nio.file.Path> classPath;
    private final boolean verifyByDefault;
    /** javac's account of the classes the compiled classfiles mention, which the hierarchy walks take first. */
    private Map<String, org.strata.java.bytecode.frontend.TypeFacts> javacTypes = Map.of();

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

    /** Fully qualify a source-form type name against this compilation's classes (program classes print as fqns). */
    String qualifyTypeName(String name, String descriptorFqn) {
        String qualified = qualifyTypeName(name);
        if (descriptorFqn != null && !descriptorFqn.equals(qualified)
                && programClassModels.containsKey(descriptorFqn) && programClassModels.containsKey(qualified)) {
            return descriptorFqn;
        }
        return qualified;
    }

    String qualifyTypeName(String name) {
        String alias = contractAliases.get(name);
        if (alias != null) {
            return alias;
        }
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

    void useJavacTypes(Map<String, org.strata.java.bytecode.frontend.TypeFacts> types) {
        this.javacTypes = types;
    }

    /**
     * What javac knew of a class outside both the program and the JDK, or null;
     * then reflection answers, which sees only the classes of this frontend's own JVM,
     * and may find another class of the same name there.
     */
    private org.strata.java.bytecode.frontend.TypeFacts javacFacts(String fqn) {
        return programClassModels.containsKey(fqn) ? null : javacTypes.get(fqn);
    }

    /** Direct interface fqns of {@code fqn} (classfile if a program type, else {@link #javacFacts}, else reflection). */
    List<String> directInterfacesOf(String fqn) {
        var facts = javacFacts(fqn);
        if (facts != null) {
            return new java.util.ArrayList<>(facts.interfaces());
        }
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

    /** True iff {@code fqn} is an interface (classfile flags if a program type, else {@link #javacFacts}, else reflection). */
    boolean isInterface(String fqn) {
        var facts = javacFacts(fqn);
        if (facts != null) {
            return facts.isInterface();
        }
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
     * their classfiles; everything else from {@link #javacFacts}, else via
     * reflection without initialization.
     */
    String superclassOf(String fqn) {
        String prog = programSuper.get(fqn);
        if (prog != null) {
            return prog;
        }
        var facts = javacFacts(fqn);
        if (facts != null) {
            return facts.superclass();
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

    /**
     * True iff {@code fqn} is {@code ancestor} or reaches it through its
     * superclasses and interfaces (breadth-first, each type visited once).
     */
    boolean isSubtypeOf(String fqn, String ancestor) {
        var seen = new java.util.HashSet<String>();
        var queue = new java.util.ArrayDeque<String>();
        queue.add(fqn);
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!seen.add(c)) {
                continue;
            }
            if (c.equals(ancestor)) {
                return true;
            }
            String sup = superclassOf(c);
            if (sup != null) {
                queue.add(sup);
            }
            queue.addAll(directInterfacesOf(c));
        }
        return false;
    }

    /** True iff {@code fqn} has {@code java.lang.Throwable} on its superclass chain. */
    boolean isThrowableSubtype(String fqn) {
        return isSubclassOf(fqn, "java.lang.Throwable");
    }

    /** {@link #commonSuperclass}, refusing when there is none. */
    String leastCommonAncestor(String a, String b) {
        String c = commonSuperclass(a, b);
        if (c == null) {
            throw new JavaViolationException("thrown types share no common ancestor");
        }
        return c;
    }

    /**
     * Least common ancestor of two classes along their superclass chains.
     * Interfaces are irrelevant: thrown types are classes with single
     * inheritance. The chains meet at worst at {@code java.lang.Object}, unless a
     * superclass cannot be resolved: then null.
     */
    String commonSuperclass(String a, String b) {
        var bChain = new java.util.LinkedHashSet<String>();
        for (String c = b; c != null; c = superclassOf(c)) {
            bChain.add(c);
        }
        for (String c = a; c != null; c = superclassOf(c)) {
            if (bChain.contains(c)) {
                return c;
            }
        }
        return null;
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
        return findCallee(receiverFqn, name, null);
    }

    /**
     * As {@link #findCallee(String, String)}, but preferring the declaration
     * whose erased descriptor is exactly {@code desc} -- the descriptor javac
     * put on the invoke instruction, i.e. the overload it bound. Falls back to
     * the nearest same-named declaration when no descriptor matches (a
     * covariant-return override is reached through its bridge's descriptor).
     */
    Callee findCallee(String receiverFqn, String name, java.lang.constant.MethodTypeDesc desc) {
        if (desc != null) {
            for (String fqn = receiverFqn; fqn != null; fqn = programSuper.get(fqn)) {
                var cm = programClassModels.get(fqn);
                if (cm == null) {
                    break;
                }
                for (var m : cm.methods()) {
                    if (m.methodName().stringValue().equals(name)
                            && m.methodTypeSymbol().parameterList()
                                    .equals(desc.parameterList())
                            && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                    | java.lang.classfile.ClassFile.ACC_BRIDGE
                                    | java.lang.classfile.ClassFile.ACC_STATIC)) == 0) {
                        return new Callee(fqn, m);
                    }
                }
            }
        }
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
        return findInterfaceCallee(receiverFqn, name, desc);
    }

    private Callee findInterfaceCallee(String receiverFqn, String name, java.lang.constant.MethodTypeDesc desc) {
        var queue = new java.util.ArrayDeque<String>();
        var seen = new java.util.HashSet<String>();
        for (String fqn = receiverFqn; fqn != null && programClassModels.containsKey(fqn); fqn = programSuper.get(fqn)) {
            queue.addAll(directInterfacesOf(fqn));
        }
        Callee byName = null;
        while (!queue.isEmpty()) {
            String iface = queue.poll();
            var cm = programClassModels.get(iface);
            if (cm == null || !seen.add(iface)) {
                continue;
            }
            for (var m : cm.methods()) {
                if (!m.methodName().stringValue().equals(name)
                        || (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE
                                | java.lang.classfile.ClassFile.ACC_STATIC)) != 0) {
                    continue;
                }
                if (desc == null || m.methodTypeSymbol().parameterList().equals(desc.parameterList())) {
                    return new Callee(iface, m);
                }
                if (byName == null) {
                    byName = new Callee(iface, m);
                }
            }
            queue.addAll(directInterfacesOf(iface));
        }
        return byName;
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
     * The resolved (stacked) opt-out status for a classfile method: the
     * method's own {@code @Verify} wins unless the class's says
     * {@code overrideChildren}; with neither, the class-level value, then
     * verify-by-default, decides. Readable from the classfile because the
     * spec annotations have CLASS retention.
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
     * Relocated source @Contract class fqn -> its contractee fqn. A contract method's
     * bytecode names its OWN class -- `this` is typed by it and its field reads say
     * `getfield ContractClass.value` -- while the translation is of the contractee's
     * method, whose composite carries those fields. Every reference to the
     * contract class is redirected to the contractee.
     */
    final Map<String, String> contracteeOf = new java.util.HashMap<>();
    /** Library type fqn -> its @Contract declaration (see Declarations.libraryContracts). */
    final Map<String, Declarations.ContractDecl> libraryContracts = new java.util.HashMap<>();

    /**
     * The library contract for {@code name}, given either as the library type's fqn or
     * as its sort -- the short name a receiver's static type resolves to (see
     * BodyTranslator.staticTypeFqnOf), which is how a call site sees it.
     */
    Declarations.ContractDecl libraryContractOf(String name) {
        var direct = libraryContracts.get(name);
        if (direct != null) {
            return direct;
        }
        for (var e : libraryContracts.entrySet()) {
            if (libraryContractSort(e.getKey()).equals(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** The library type a library contract declaration stands for. */
    String contracteeFqnOfLibraryContract(Declarations.ContractDecl contract) {
        for (var e : libraryContracts.entrySet()) {
            if (e.getValue() == contract) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The sort a library contractee is emitted as: the short name every use of the type gets. */
    String libraryContractSort(String fqn) {
        return qualifyTypeName(TypeRef.ofDescriptor("L" + fqn.replace('.', '/') + ";").nameWithScope());
    }

    /** The type-parameter names a PROGRAM class declares, in order; empty otherwise. */
    List<String> classTypeParams(String fqn) {
        var cm = programClassModels.get(fqn);
        if (cm == null) {
            var contract = libraryContractOf(fqn);
            return contract == null ? List.of() : Declarations.typeParamNamesOf(contract.decl().cm());
        }
        return Declarations.typeParamNamesOf(cm);
    }

    /**
     * The type arguments of a {@code new} site, reconstructed the way javac's
     * diamond inference does: unify the CONSTRUCTOR's generic signature against the
     * actual argument types.
     *
     * <p>The {@code new} instruction itself is erased -- it names the raw class and
     * the constructor descriptor is erased too -- but the constructor's
     * {@code Signature} attribute survives ({@code (TT;)V}), and so do the argument
     * types the translator already tracks. That is enough to solve for each class
     * type parameter, including through a nested instantiation
     * ({@code Box<List<String>>}).
     *
     * <p>Returns one entry per declared type parameter, in declaration order, with
     * {@code null} where the arguments do not determine it (a no-argument
     * constructor, or a parameter the constructor never mentions). The caller
     * decides what to do with an unresolved slot.
     */
    List<String> inferNewTypeArgs(String fqn, java.lang.constant.MethodTypeDesc ctorDesc,
                                  List<String> argRenderedTypes) {
        var params = classTypeParams(fqn);
        if (params.isEmpty()) {
            return List.of();
        }
        var solved = new java.util.HashMap<String, String>();
        var ctorSig = constructorSignature(fqn, ctorDesc);
        if (ctorSig != null) {
            int n = Math.min(ctorSig.arguments().size(), argRenderedTypes.size());
            for (int i = 0; i < n; i++) {
                unify(ctorSig.arguments().get(i), argRenderedTypes.get(i), params, solved);
            }
        }
        var out = new java.util.ArrayList<String>();
        for (String p : params) {
            out.add(solved.get(p));
        }
        return out;
    }

    /** The generic signature of {@code fqn}'s constructor with this erased descriptor, or null. */
    private java.lang.classfile.MethodSignature constructorSignature(
            String fqn, java.lang.constant.MethodTypeDesc ctorDesc) {
        var cm = programClassModels.get(fqn);
        if (cm == null || ctorDesc == null) {
            return null;
        }
        for (var m : cm.methods()) {
            if (!m.methodName().stringValue().equals("<init>")
                    || !m.methodTypeSymbol().equals(ctorDesc)) {
                continue;
            }
            return m.findAttribute(java.lang.classfile.Attributes.signature())
                    .map(sa -> {
                        try {
                            return java.lang.classfile.MethodSignature
                                    .parseFrom(sa.signature().stringValue());
                        } catch (RuntimeException ignore) {
                            return null;
                        }
                    })
                    .orElse(null);
        }
        return null;
    }

    /**
     * Match one constructor parameter's generic signature against the actual
     * argument's rendered type, binding any type parameter it names. A bare type
     * variable binds to the whole argument type; a parameterised type recurses
     * pairwise into its arguments, so {@code Box<T>} against {@code Box<String>}
     * binds {@code T} to {@code String}. The first binding wins, matching javac's
     * left-to-right resolution.
     */
    private static void unify(java.lang.classfile.Signature param, String actual,
                              List<String> typeParams, java.util.Map<String, String> solved) {
        if (actual == null) {
            return;
        }
        if (param instanceof java.lang.classfile.Signature.TypeVarSig tv) {
            if (typeParams.contains(tv.identifier())) {
                solved.putIfAbsent(tv.identifier(), TypeRef.readType(actual));
            }
            return;
        }
        if (param instanceof java.lang.classfile.Signature.ClassTypeSig cts
                && !cts.typeArgs().isEmpty()) {
            var actualArgs = TypeRef.typeArguments(actual);
            if (actualArgs.size() != cts.typeArgs().size()) {
                return;
            }
            for (int i = 0; i < actualArgs.size(); i++) {
                if (cts.typeArgs().get(i) instanceof java.lang.classfile.Signature.TypeArg.Bounded b) {
                    unify(b.boundType(), actualArgs.get(i), typeParams, solved);
                }
            }
        }
    }

    /**
     * Separator before each erased parameter type in an overloaded method's
     * emitted name. {@code '?'} cannot appear in a Java identifier (JLS 3.8),
     * so the mangling is injective.
     */
    static final String MANGLE_SEPARATOR = "?";

    /**
     * The suffix that distinguishes an overloaded method
     * from its same-named siblings, appended to the emitted Laurel procedure
     * name so a by-name call ({@code Class_m} for a static, {@code obj#m} for
     * an instance) still selects the right one. Empty unless the name is
     * actually overloaded; otherwise {@link #MANGLE_SEPARATOR} then each
     * parameter's ERASED type.
     *
     * <p>Erased on purpose: an override has the same erased descriptor as the
     * base (JLS override rules), so it mangles to the SAME name and dispatch
     * families still line up. Only overLOADs get different names. Measured over
     * the override chain's ROOT (see {@link #overloadScope}), so a base, every
     * override of it, and every call site agree.
     */
    String overloadSuffix(String declaringFqn, java.lang.classfile.MethodModel m) {
        if (!isOverloaded(declaringFqn, m)) {
            return "";
        }
        var sb = new StringBuilder();
        for (var pt : m.methodTypeSymbol().parameterList()) {
            sb.append(MANGLE_SEPARATOR).append(erasedTypeName(pt));
        }
        return sb.toString();
    }

    /** The rendering of an erased parameter type in a mangled name: a dotted fqn, {@code int}, {@code X[]}. */
    static String erasedTypeName(java.lang.constant.ClassDesc cd) {
        if (cd.isPrimitive()) {
            return cd.displayName();
        }
        if (cd.isArray()) {
            return erasedTypeName(cd.componentType()) + "[]";
        }
        String desc = cd.descriptorString();
        return JavaNames.dottedFqn(desc.substring(1, desc.length() - 1));
    }

    /** One same-named member of a type in a closure walk: erased params and whether it can be overridden. */
    private record Member(String params, boolean inheritable) {
    }

    /**
     * The methods named {@code name} that {@code fqn} DECLARES (not inherits),
     * synthetic and bridge methods excluded. Program types read their classfile,
     * everything else {@link #javacFacts} or reflection, matching the rest of this class.
     */
    private List<Member> declaredMembers(String fqn, String name) {
        var facts = javacFacts(fqn);
        if (facts != null) {
            return facts.methods().stream().filter(m -> m.name().equals(name))
                    .map(m -> new Member(m.params(), m.inheritable())).toList();
        }
        var out = new java.util.ArrayList<Member>();
        var cm = programClassModels.get(fqn);
        if (cm != null) {
            for (var m : cm.methods()) {
                int f = m.flags().flagsMask();
                if (!m.methodName().stringValue().equals(name)
                        || (f & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE)) != 0) {
                    continue;
                }
                out.add(new Member(paramDescriptors(m), (f & (java.lang.classfile.ClassFile.ACC_STATIC
                        | java.lang.classfile.ClassFile.ACC_PRIVATE)) == 0));
            }
            return out;
        }
        Class<?> c = loadClassReflectively(fqn);
        if (c != null) {
            for (var m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.isSynthetic() || m.isBridge()) {
                    continue;
                }
                int mod = m.getModifiers();
                out.add(new Member(paramDescriptors(m), !java.lang.reflect.Modifier.isStatic(mod)
                        && !java.lang.reflect.Modifier.isPrivate(mod)));
            }
        }
        return out;
    }

    /**
     * {@code fqn} and every supertype, superclasses AND interfaces, each once.
     * This is the closure javac's {@code Types.closure} computes; walking the
     * superclass chain alone misses a same-named method declared on an
     * implemented interface.
     */
    private java.util.LinkedHashSet<String> typeClosure(String fqn) {
        var out = new java.util.LinkedHashSet<String>();
        var work = new java.util.ArrayDeque<String>();
        work.add(fqn);
        while (!work.isEmpty()) {
            String t = work.poll();
            if (!out.add(t)) {
                continue;
            }
            String sup = superclassOf(t);
            if (sup != null) {
                work.add(sup);
            }
            work.addAll(directInterfacesOf(t));
        }
        return out;
    }

    /**
     * The type whose closure decides {@code m}'s overload suffix: the ROOT
     * declaration of {@code m}'s override chain -- the topmost supertype declaring
     * an inheritable method with {@code m}'s erased parameters, or with those of a
     * javac bridge in the declaring class that forwards to {@code m} (an override
     * whose erasure differs from the overridden method's, as in {@code Sub extends
     * Base<Integer>} overriding {@code Base<T>.m(T)}) -- or the declaring class
     * itself when {@code m} overrides nothing.
     *
     * <p>Not the declaring class, because "overloaded" measured there is not stable
     * along an override chain. With {@code Base.m(int)} and {@code Sub extends Base}
     * declaring both {@code m(int)} (an override) and {@code m(String)} (a new
     * overload), closure(Base) sees one signature so {@code Base.m(int)} would stay
     * bare while closure(Sub) sees two so {@code Sub.m(int)} would become {@code m?int}.
     * Distinct names mean Strata never pairs them, so CheckOverrideRefinement never
     * generates the Liskov obligation, and the call site resolves to the base's
     * contract while the override runs: a false {@code check} PROVED
     * (OverrideWithSubclassOverload). Keying on the root makes base and override
     * share a scope, hence a name; the subclass-added overload is its own root and
     * still gets a distinct one.
     *
     * <p>Known gap: a name with two INDEPENDENT roots (two interfaces
     * each declaring a different {@code m}, both overridden by one class) cannot be
     * agreed with by any symbol-local scheme.
     */
    private String overloadScope(String declaringFqn, java.lang.classfile.MethodModel m) {
        int f = m.flags().flagsMask();
        if ((f & (java.lang.classfile.ClassFile.ACC_STATIC
                | java.lang.classfile.ClassFile.ACC_PRIVATE)) != 0) {
            return declaringFqn;
        }
        String name = m.methodName().stringValue();
        var paramLists = new java.util.HashSet<String>();
        paramLists.add(paramDescriptors(m));
        paramLists.addAll(forwardingBridgeParams(declaringFqn, m));
        String scope = declaringFqn;
        for (String t : typeClosure(declaringFqn)) {
            if (t.equals(scope)) {
                continue;
            }
            boolean declaresOverridden = declaredMembers(t, name).stream()
                    .anyMatch(x -> x.inheritable() && paramLists.contains(x.params()));
            // Keep the HIGHEST declarer: move up only when the candidate is a
            // supertype of the one held, so the result does not depend on walk order.
            if (declaresOverridden && typeClosure(scope).contains(t)) {
                scope = t;
            }
        }
        return scope;
    }

    private List<String> forwardingBridgeParams(String declaringFqn, java.lang.classfile.MethodModel m) {
        var out = new java.util.ArrayList<String>();
        var cm = programClassModels.get(declaringFqn);
        if (cm == null) {
            return out;
        }
        String name = m.methodName().stringValue();
        String desc = m.methodType().stringValue();
        String owner = cm.thisClass().asInternalName();
        for (var b : cm.methods()) {
            if ((b.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_BRIDGE) == 0
                    || !b.methodName().stringValue().equals(name) || b == m) {
                continue;
            }
            boolean forwards = b.code().stream()
                    .flatMap(c -> c.elementStream())
                    .anyMatch(e -> e instanceof java.lang.classfile.instruction.InvokeInstruction inv
                            && inv.owner().asInternalName().equals(owner)
                            && inv.name().stringValue().equals(name)
                            && inv.type().stringValue().equals(desc));
            if (forwards) {
                out.add(paramDescriptors(b));
            }
        }
        return out;
    }

    /**
     * Whether {@code m}'s name denotes more than one distinct ERASED signature in
     * the closure of its {@linkplain #overloadScope override-chain root} -- i.e. it
     * is overLOADed, as opposed to a lone method or a pure overRIDE, which share one
     * signature.
     *
     * <p>Only methods ROOTED in the program under verification are mangled: a library
     * method is modeled by a builtin {@code @Contract} declaring one canonical entry
     * per name, so a call must emit the bare name to resolve against it. Gating on the
     * root rather than the declaring class also keeps a user override of a library
     * method ({@code equals(Object)}) on the library's canonical name. The signature
     * COUNT still spans library supertypes, so a user {@code toString(int)} alongside
     * {@code Object.toString()} is caught.
     *
     * <p>Opted-out methods COUNT: a skipped {@code @Pure} method keeps an opaque
     * declaration, so it is emitted under this same name and would collide.
     */
    boolean isOverloaded(String declaringFqn, java.lang.classfile.MethodModel m) {
        String scope = overloadScope(declaringFqn, m);
        if (!programClassModels.containsKey(scope)) {
            return false;
        }
        String name = m.methodName().stringValue();
        var signatures = new java.util.LinkedHashSet<String>();
        for (String t : typeClosure(scope)) {
            for (Member x : declaredMembers(t, name)) {
                signatures.add(x.params());
            }
        }
        return signatures.size() > 1;
    }

    /** The bare Java name behind an emitted (possibly mangled) Laurel method name. */
    static String demangle(String laurelName) {
        int sep = laurelName.indexOf(MANGLE_SEPARATOR);
        return sep < 0 ? laurelName : laurelName.substring(0, sep);
    }

    // Instance calls that may reach an override are deliberately NOT refused.
    // An `obj#method` call resolves against the receiver's DECLARED type, but
    // Laurel dispatches an overridden composite method on the receiver's runtime
    // tag (LiftInstanceProcedures), and CheckOverrideRefinement discharges the
    // Liskov obligation that makes reasoning against the declared type's contract
    // sound. PolyDispatchSoundness pins both halves.

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

    // A call whose name resolves to more than one erased signature over the
    // receiver's supertype closure is deliberately NOT refused: an overloaded name
    // carries its erased parameter types (see overloadSuffix), so by-name Laurel
    // resolution tells the overloads apart. The signature counting lives in
    // isOverloaded.

}
