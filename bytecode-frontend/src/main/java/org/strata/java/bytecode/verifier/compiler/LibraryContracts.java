package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Common;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.JarFileEntry;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.frontend.MethodBodyFactory;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The contract library: {@code object-contract.java} plus the
 * {@code contractSourcePath} sources, compiled with a javac step of their own
 * (separate from the program compile) and indexed by contractee.
 *
 * <p>Loading is lazy when there are no user sources: the first lookup
 * compiles {@code object-contract.java}, so a program that makes no library
 * call never compiles contracts. With user sources the caller loads eagerly
 * right after {@link #bind}. Classfile bytes and the index are cached
 * statically per (sources, content hashes, classpath, classpath stamps);
 * every run parses its
 * own {@link ClassModel}s from the cached bytes. Compile, index, and I/O
 * errors while loading are reported through the bound {@link Reporter},
 * never thrown; {@link #fatalLoadError()} tells the caller the run must end
 * without a verdict.
 *
 * <p>Lookups return a {@link ContractMember} or null; a miss has no side
 * effects. Members the translation will need are queued with
 * {@link #demand}.
 */
public final class LibraryContracts {

    /**
     * A value whose Laurel type is a type variable never reaches a library
     * call as the argument for a reference parameter that is not itself a
     * type variable: Strata has no bound slot on type parameters, so such a
     * value cannot stand where the {@code Object} composite is expected.
     */
    static final boolean REFUSE_TYPE_VAR_REFERENCE_ARGS = true;
    /**
     * A string literal or concatenation never reaches a library call as a
     * reference argument: it is a Laurel string, and Strata rejects it where
     * the {@code String} or {@code Object} composite is expected.
     */
    static final boolean REFUSE_LITERAL_REFERENCE_ARGS = true;

    /** The built-in array model class of {@code object-contract.java} (a helper class). */
    static final String JARRAY_INTERNAL = "org/strata/jverify/builtin/JArray";

    private static final String OBJECT_CONTRACT_RESOURCE = "/object-contract.java";
    private static final String SPEC_INTERFACE_PREFIX = "org/strata/jverify/JVerify$";

    /** Loaded contract libraries; javac-failed loads are never inserted. */
    private static final ConcurrentHashMap<Key, LoadedBytes> CACHE = new ConcurrentHashMap<>();

    /**
     * Cache key: ordered source URIs, the SHA-256 of each source's content,
     * the javac classpath, and a {@link #stamp} per classpath entry, so a
     * classpath whose contents changed compiles again.
     */
    record Key(List<String> sourceUris, List<String> contentHashes, List<Path> classPath,
               List<String> classPathStamps) {
    }

    /** An index diagnostic, stored with the cached load and replayed once per run. */
    record IndexError(URI uri, Range range, String message) {
    }

    /** One indexed {@code @Contract} class. */
    record ContractClass(String contractInternal, String contracteeFqn, String sort,
                         boolean pure, URI uri) {
    }

    /** The contractee index of one load (strings only). */
    record Index(Map<String, ContractClass> contractsByContractee,
                 Map<String, ContractClass> contractsByInternal,
                 Set<String> helperClasses,
                 Map<String, String> aliases,
                 Map<String, String> descriptorMap) {
    }

    /** One cached load: classfile bytes, their source URIs, the index, and its errors. */
    record LoadedBytes(Map<String, byte[]> classBytes, Map<String, URI> classUris,
                       Index index, List<IndexError> indexErrors) {
    }

    private final boolean disabled;
    private final JavaFileObject objectContract;
    private final List<JavaFileObject> userSources;
    private final List<Path> classPath;

    private ClassResolver resolver;
    private Set<String> programContractTargets = Set.of();
    private Reporter reporter;
    private URI anchor;
    private boolean bound;

    private boolean attempted;
    private LoadedBytes loadedBytes;
    private boolean fatalLoadError;

    private final ArrayDeque<ContractMember> demandQueue = new ArrayDeque<>();
    private final Set<String> demandedKeys = new LinkedHashSet<>();
    /** sortOrFqn_laurelName -> @Pure, for every member a lookup returned. */
    private final Map<String, Boolean> pureKeys = new HashMap<>();

    private LibraryContracts(boolean disabled, JavaFileObject objectContract,
                             List<JavaFileObject> userSources, List<Path> classPath) {
        this.disabled = disabled;
        this.objectContract = objectContract;
        this.userSources = List.copyOf(userSources);
        this.classPath = List.copyOf(classPath);
    }

    /** No contracts: never loads, ignores {@link #bind}, and every lookup misses. */
    public static LibraryContracts none() {
        return new LibraryContracts(true, null, List.of(), List.of());
    }

    /**
     * Contracts from {@code objectContract} (always loaded lazily on its own)
     * plus {@code userSources} (the {@code contractSourcePath} sources, loaded
     * eagerly), compiled against {@code classPath}.
     */
    public static LibraryContracts of(JavaFileObject objectContract, List<JavaFileObject> userSources,
                                      List<Path> classPath) {
        return new LibraryContracts(false, objectContract, userSources, classPath);
    }

    /** {@code object-contract.java} as a source with URI {@code string:///object-contract.java}. */
    public static JavaFileObject objectContractSource() {
        String content = Common.getResourceFile(LibraryContracts.class, OBJECT_CONTRACT_RESOURCE);
        if (content == null) {
            throw new IllegalStateException("object-contract.java resource missing");
        }
        return new SourceFile(OBJECT_CONTRACT_RESOURCE, content);
    }

    /**
     * The {@code .java} sources of the {@code contractSourcePath} entries, in
     * entry order, each entry's sources sorted by URI string. An entry is
     * resolved against {@code workingDirectory}; a {@code .jar} contributes
     * its {@code .java} entries, anything else is walked for {@code .java}
     * files, named by their path relative to the entry.
     *
     * @throws IllegalArgumentException when an entry does not exist
     * @throws UncheckedIOException when an entry or one of its sources cannot
     *         be read (a corrupt jar, a directory named {@code *.jar}, a
     *         source that is not UTF-8); the message names the path
     */
    public static List<JavaFileObject> collectUserSources(Path workingDirectory,
                                                          Collection<Path> contractSourcePath) {
        var out = new ArrayList<JavaFileObject>();
        for (Path entry : contractSourcePath) {
            Path resolved = workingDirectory.resolve(entry);
            if (!Files.exists(resolved)) {
                throw new IllegalArgumentException("Could not find path: " + resolved);
            }
            var batch = new ArrayList<JavaFileObject>();
            if (resolved.toString().endsWith(".jar")) {
                if (Files.isDirectory(resolved)) {
                    throw unreadable(resolved, "a directory, not a jar");
                }
                try (var jar = new JarFile(resolved.toFile())) {
                    jar.stream()
                            .filter(je -> je.getName().endsWith(".java"))
                            .forEach(je -> batch.add(new JarFileEntry(resolved, je)));
                } catch (IOException e) {
                    throw unreadable(resolved, e);
                }
            } else {
                List<Path> files;
                try (Stream<Path> walk = Files.walk(resolved)) {
                    // Sorted: Files.walk order is filesystem-dependent, and
                    // per-file index errors (a duplicate @Contract) follow it.
                    files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
                } catch (IOException | UncheckedIOException e) {
                    throw unreadable(resolved, e);
                }
                for (Path p : files) {
                    String content;
                    try {
                        content = Files.readString(p);
                    } catch (IOException e) {
                        throw unreadable(p, e);
                    }
                    // "string://" + relative path, '\' normalized, as the
                    // source frontend names directory contract sources.
                    String relativePath = resolved.relativize(p).toString().replace('\\', '/');
                    batch.add(new SourceFile(relativePath, content));
                }
            }
            batch.sort(Comparator.comparing(f -> f.toUri().toString()));
            out.addAll(batch);
        }
        return out;
    }

    /** {@code Could not read contract source <where>: <reason>}, caused by {@code e}'s I/O failure. */
    private static UncheckedIOException unreadable(Object where, Exception e) {
        IOException cause = e instanceof UncheckedIOException u ? u.getCause() : (IOException) e;
        return new UncheckedIOException("Could not read contract source " + where + ": " + reasonOf(cause),
                cause);
    }

    private static UncheckedIOException unreadable(Path path, String reason) {
        String message = "Could not read contract source " + path + ": " + reason;
        return new UncheckedIOException(message, new IOException(message));
    }

    /** An I/O failure's reason for a diagnostic; a decoding failure names the expected encoding. */
    private static String reasonOf(IOException cause) {
        if (cause instanceof java.nio.charset.CharacterCodingException) {
            return "not valid UTF-8";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    // --- Binding and loading ---

    /** True for {@link #none()}: no contracts are ever loaded or applied. */
    boolean disabled() {
        return disabled;
    }

    /** True iff there are {@code contractSourcePath} sources (loaded eagerly). */
    boolean hasUserSources() {
        return !userSources.isEmpty();
    }

    /**
     * Bind this run's resolver, the program's own {@code @Contract} targets
     * (whose library contracts are not applied), the reporter that receives
     * load diagnostics, and the URI the lazy-load warning is anchored at.
     */
    void bind(ClassResolver resolver, Set<String> programContractTargets, Reporter reporter, URI anchor) {
        if (disabled) {
            return;
        }
        this.resolver = resolver;
        this.programContractTargets = Set.copyOf(programContractTargets);
        this.reporter = reporter;
        this.anchor = anchor;
        this.bound = true;
    }

    /**
     * Compile and index the contract sources, at most once per instance.
     * Never throws for a compile, index, or I/O error: errors go to the bound
     * reporter, and {@link #fatalLoadError()} is set when the run must end
     * without a verdict (an I/O error always ends it). After a failed load
     * every lookup misses.
     */
    void ensureLoaded() {
        if (disabled) {
            return;
        }
        requireBound();
        if (attempted) {
            return;
        }
        attempted = true;
        List<JavaFileObject> sources = sources();
        var outcome = new CompileOutcome();
        LoadedBytes loaded;
        try {
            loaded = CACHE.computeIfAbsent(keyOf(sources), k -> compileAndIndex(sources, outcome));
        } catch (UncheckedIOException e) {
            // A source or class path entry that became unreadable after
            // collectUserSources (a jar entry is read only here).
            reporter.reportError(anchor, Reporter.NO_RANGE, e.getMessage());
            fatalLoadError = true;
            return;
        }
        if (loaded == null) {
            reportCompileFailure(outcome);
            return;
        }
        loadedBytes = loaded;
        for (var error : loaded.indexErrors()) {
            reporter.reportError(error.uri(), error.range(), error.message());
            fatalLoadError = true;
        }
    }

    /** True once the contract classfiles are available. */
    boolean loaded() {
        return loadedBytes != null;
    }

    /** True when a load error must end the run without a verdict. */
    boolean fatalLoadError() {
        return fatalLoadError;
    }

    /** The URIs of the loaded contract sources (empty until loaded). */
    List<URI> loadedUris() {
        if (loadedBytes == null) {
            return List.of();
        }
        return sources().stream().map(JavaFileObject::toUri).toList();
    }

    /** The cached load this instance uses, or null; identity shows cache reuse. */
    LoadedBytes loadedBytes() {
        return loadedBytes;
    }

    /** The contract index aliases (printed contract-class name -> contractee sort); empty until loaded. */
    Map<String, String> aliases() {
        return loadedBytes == null ? Map.of() : loadedBytes.index().aliases();
    }

    /** The indexed contract class with this internal name, or null (also when not loaded). */
    ContractClass contractClassOf(String internalName) {
        return loadedBytes == null ? null : loadedBytes.index().contractsByInternal().get(internalName);
    }

    /** A fresh parse of a loaded contract-source classfile, or null when there is none. */
    ClassModel classBytes(String internalName) {
        if (loadedBytes == null) {
            return null;
        }
        byte[] bytes = loadedBytes.classBytes().get(internalName);
        return bytes == null ? null : ClassFile.of().parse(bytes);
    }

    private void requireBound() {
        if (!bound) {
            throw new IllegalStateException("LibraryContracts used before bind");
        }
    }

    private List<JavaFileObject> sources() {
        var out = new ArrayList<JavaFileObject>();
        out.add(objectContract);
        out.addAll(userSources);
        return out;
    }

    private Key keyOf(List<JavaFileObject> sources) {
        var uris = new ArrayList<String>();
        var hashes = new ArrayList<String>();
        for (var source : sources) {
            uris.add(source.toUri().toString());
            hashes.add(sha256(contentOf(source)));
        }
        var stamps = new ArrayList<String>();
        for (var entry : classPath) {
            stamps.add(stamp(entry));
        }
        return new Key(List.copyOf(uris), List.copyOf(hashes), classPath, List.copyOf(stamps));
    }

    /**
     * A source's text. An I/O failure becomes an {@link UncheckedIOException}
     * naming the source, including the one {@link JarFileEntry} wraps in a
     * plain {@link RuntimeException}.
     */
    private static String contentOf(JavaFileObject source) {
        try {
            return source.getCharContent(true).toString();
        } catch (IOException e) {
            throw unreadable(source.toUri(), e);
        } catch (UncheckedIOException e) {
            throw unreadable(source.toUri(), e.getCause());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw unreadable(source.toUri(), io);
            }
            throw e;
        }
    }

    /**
     * A classpath entry's size and modification time: of the file, or the
     * SHA-256 over the relative path, size and modification time of every
     * file under the directory; {@code "absent"} when the entry is missing.
     */
    private static String stamp(Path entry) {
        try {
            if (!Files.isDirectory(entry)) {
                return Files.exists(entry) ? fileStamp(entry) : "absent";
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(entry)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            var listing = new StringBuilder();
            for (var file : files) {
                listing.append(entry.relativize(file)).append(' ').append(fileStamp(file)).append('\n');
            }
            return sha256(listing.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read class path entry " + entry + ": "
                    + reasonOf(e), e);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException("Could not read class path entry " + entry + ": "
                    + reasonOf(e.getCause()), e.getCause());
        }
    }

    private static String fileStamp(Path file) throws IOException {
        return Files.size(file) + "@" + Files.getLastModifiedTime(file);
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a failed javac step left behind for this instance to report. */
    private static final class CompileOutcome {
        boolean failed;
        List<Diagnostic<? extends JavaFileObject>> errors = List.of();
    }

    /** Compile into a temp dir, read the classfile bytes, and index; null when javac fails. */
    private LoadedBytes compileAndIndex(List<JavaFileObject> sources, CompileOutcome outcome) {
        Path outputDir = null;
        try {
            outputDir = Files.createTempDirectory("bytecode-contracts");
            var result = BytecodeFrontend.compile(sources, classPath, outputDir, Writer.nullWriter());
            if (!result.success()) {
                outcome.failed = true;
                outcome.errors = result.diagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .toList();
                return null;
            }
            Map<String, byte[]> classBytes = new TreeMap<>();
            List<Path> classFiles;
            try (Stream<Path> walk = Files.walk(outputDir)) {
                classFiles = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
            }
            for (Path p : classFiles) {
                byte[] bytes = Files.readAllBytes(p);
                classBytes.put(ClassFile.of().parse(bytes).thisClass().asInternalName(), bytes);
            }
            return buildLoad(sources, classBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compile contract sources: " + reasonOf(e), e);
        } finally {
            if (outputDir != null) {
                deleteRecursively(outputDir);
            }
        }
    }

    private void reportCompileFailure(CompileOutcome outcome) {
        if (hasUserSources()) {
            if (outcome.errors.isEmpty()) {
                reporter.reportError(anchor, Reporter.NO_RANGE,
                        "Java compilation had an error: javac reported failure with no error diagnostic");
            }
            for (var d : outcome.errors) {
                URI uri = d.getSource() != null ? d.getSource().toUri() : anchor;
                var start = new Position((int) Math.max(1, d.getLineNumber()),
                        (int) Math.max(1, d.getColumnNumber()));
                var range = new Range(start, new Position(start.line(), start.character() + 1));
                reporter.reportError(uri, range, "Java compilation had an error: " + d.getMessage(null));
            }
            fatalLoadError = true;
        } else {
            String first = outcome.errors.isEmpty()
                    ? "javac reported failure with no error diagnostic"
                    : outcome.errors.getFirst().getMessage(null);
            reporter.reportWarning(anchor, Reporter.NO_RANGE, "built-in contracts are unavailable: " + first);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- Index ---

    private static LoadedBytes buildLoad(List<JavaFileObject> sources, Map<String, byte[]> classBytes) {
        var sourceUris = sources.stream().map(s -> s.toUri()).toList();
        var models = new LinkedHashMap<String, ClassModel>();
        var classUris = new HashMap<String, URI>();
        for (var e : classBytes.entrySet()) {
            ClassModel cm = ClassFile.of().parse(e.getValue());
            models.put(e.getKey(), cm);
            URI uri = sourceUriOf(cm, sourceUris);
            if (uri != null) {
                classUris.put(e.getKey(), uri);
            }
        }
        // Source order: by source position in the compile, then declaration line.
        var ordered = new ArrayList<>(models.keySet());
        ordered.sort(Comparator
                .comparingInt((String n) -> {
                    int i = sourceUris.indexOf(classUris.get(n));
                    return i < 0 ? Integer.MAX_VALUE : i;
                })
                .thenComparingInt(n -> MethodBodyFactory.classDeclLine(models.get(n)))
                .thenComparing(n -> n));

        var byContractee = new LinkedHashMap<String, ContractClass>();
        var byInternal = new LinkedHashMap<String, ContractClass>();
        var helpers = new LinkedHashSet<String>();
        var aliases = new LinkedHashMap<String, String>();
        var aliasOwners = new HashMap<String, String>();
        var descriptorMap = new LinkedHashMap<String, String>();
        var errors = new ArrayList<IndexError>();
        for (String internal : ordered) {
            ClassModel cm = models.get(internal);
            if (!Declarations.hasAnnotation(cm, "Contract")) {
                helpers.add(internal);
                continue;
            }
            URI uri = classUris.get(internal);
            Range anchor = declAnchor(cm);
            String contracteeInternal = contracteeInternalOf(cm);
            if (contracteeInternal == null) {
                errors.add(new IndexError(uri, anchor, "could not find a target for @Contract class '"
                        + JavaNames.simpleName(JavaNames.dottedFqn(internal)) + "'"));
                continue;
            }
            String contracteeFqn = JavaNames.dottedFqn(contracteeInternal);
            if (byContractee.containsKey(contracteeFqn)) {
                errors.add(new IndexError(uri, anchor, "class '" + JavaNames.simpleName(contracteeFqn)
                        + "' must not be referenced by more than one @Contract annotation"));
                continue;
            }
            String printed = TypeRef.ofClassInternal(internal, false, false).nameWithScope();
            String previous = aliasOwners.get(printed);
            if (previous != null) {
                errors.add(new IndexError(uri, anchor, "contract classes '" + JavaNames.dottedFqn(previous)
                        + "' and '" + JavaNames.dottedFqn(internal) + "' share the simple name '" + printed
                        + "'; contract class simple names must be unique"));
                continue;
            }
            String sort = TypeRef.ofClassInternal(contracteeInternal, false, false).nameWithScope();
            var cc = new ContractClass(internal, contracteeFqn, sort, contractPureFlag(cm), uri);
            byContractee.put(contracteeFqn, cc);
            byInternal.put(internal, cc);
            aliases.put(printed, sort);
            aliasOwners.put(printed, internal);
            descriptorMap.put("L" + internal + ";", "L" + contracteeInternal + ";");
        }
        // Contract code's JArray<X> names the array composite, never a program class.
        if (models.containsKey(JARRAY_INTERNAL)) {
            aliases.putIfAbsent(ArrayModel.SORT, ArrayModel.SORT);
        }
        var index = new Index(Map.copyOf(byContractee), Map.copyOf(byInternal),
                Set.copyOf(helpers), Map.copyOf(aliases), Map.copyOf(descriptorMap));
        return new LoadedBytes(Map.copyOf(classBytes), Map.copyOf(classUris), index, List.copyOf(errors));
    }

    /** The class-level anchor ContractValidator uses: the declaration line, column 1. */
    private static Range declAnchor(ClassModel cm) {
        int line = MethodBodyFactory.classDeclLine(cm);
        if (line < 1) {
            return Reporter.NO_RANGE;
        }
        return new Range(new Position(line, 1), new Position(line, 2));
    }

    /**
     * The unique source whose URI ends with {@code <package path>/<SourceFile>},
     * else the unique one ending with {@code /<SourceFile>}, else null.
     */
    private static URI sourceUriOf(ClassModel cm, List<URI> sourceUris) {
        String sourceFile = cm.findAttribute(Attributes.sourceFile())
                .map(a -> a.sourceFile().stringValue())
                .orElse(null);
        if (sourceFile == null) {
            return null;
        }
        String internal = cm.thisClass().asInternalName();
        int slash = internal.lastIndexOf('/');
        if (slash >= 0) {
            URI hit = uniqueEndingWith(sourceUris, internal.substring(0, slash) + "/" + sourceFile);
            if (hit != null) {
                return hit;
            }
        }
        return uniqueEndingWith(sourceUris, "/" + sourceFile);
    }

    private static URI uniqueEndingWith(List<URI> uris, String suffix) {
        URI found = null;
        for (URI uri : uris) {
            if (uri.toString().endsWith(suffix)) {
                if (found != null) {
                    return null;
                }
                found = uri;
            }
        }
        return found;
    }

    /** The contractee's internal name: the explicit {@code @Contract} target, else the first declared supertype. */
    private static String contracteeInternalOf(ClassModel cm) {
        var anno = Declarations.annotationOf(cm, "Contract");
        if (anno != null) {
            for (var e : anno.elements()) {
                if (e.name().stringValue().equals("value") && e.value() instanceof AnnotationValue.OfClass oc) {
                    String desc = oc.classSymbol().descriptorString();
                    if (desc.startsWith("L")) {
                        return desc.substring(1, desc.length() - 1);
                    }
                }
            }
        }
        var superclass = cm.superclass().map(sc -> sc.asInternalName()).orElse(null);
        if (superclass != null && !superclass.equals("java/lang/Object")) {
            return superclass;
        }
        return cm.interfaces().isEmpty() ? null : cm.interfaces().getFirst().asInternalName();
    }

    private static boolean contractPureFlag(ClassModel cm) {
        var anno = Declarations.annotationOf(cm, "Contract");
        if (anno != null) {
            for (var e : anno.elements()) {
                if (e.name().stringValue().equals("pure") && e.value() instanceof AnnotationValue.OfBoolean b) {
                    return b.booleanValue();
                }
            }
        }
        return false;
    }

    /** A descriptor in contractee terms: contract classes (and arrays of them) become their contractees. */
    ClassDesc mapDesc(ClassDesc desc) {
        if (loadedBytes == null) {
            return desc;
        }
        String d = desc.descriptorString();
        int dims = 0;
        while (d.charAt(dims) == '[') {
            dims++;
        }
        String mapped = loadedBytes.index().descriptorMap().get(d.substring(dims));
        return mapped == null ? desc : ClassDesc.ofDescriptor("[".repeat(dims) + mapped);
    }

    private List<String> mappedParams(MethodTypeDesc desc) {
        return desc.parameterList().stream().map(p -> mapDesc(p).descriptorString()).toList();
    }

    // --- Lookup ---

    /**
     * The contract member a program instance call binds to, or null. A
     * program-class owner misses; otherwise the first contract class among
     * the owner, its superclasses, and its interfaces (skipping the program's
     * own {@code @Contract} targets) that declares a method with this name,
     * staticness, and mapped parameter list wins. The return type is ignored.
     */
    ContractMember lookupInstance(String ownerInternal, String name, MethodTypeDesc desc) {
        return lookupProgram(ownerInternal, name, desc, false);
    }

    /** The contract member a program static call binds to, or null. */
    ContractMember lookupStatic(String ownerInternal, String name, MethodTypeDesc desc) {
        return lookupProgram(ownerInternal, name, desc, true);
    }

    private ContractMember lookupProgram(String ownerInternal, String name, MethodTypeDesc desc,
                                         boolean isStatic) {
        if (disabled) {
            return null;
        }
        ensureLoaded();
        if (loadedBytes == null) {
            return null;
        }
        String owner = JavaNames.dottedFqn(ownerInternal);
        if (resolver.programClassModels.containsKey(owner)) {
            return null;
        }
        List<String> callParams = mappedParams(desc);
        for (String candidate : candidates(owner, isStatic)) {
            if (programContractTargets.contains(candidate)) {
                continue;
            }
            ContractClass cc = loadedBytes.index().contractsByContractee().get(candidate);
            if (cc == null) {
                continue;
            }
            var hit = contractMember(cc, name, isStatic, callParams);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** The owner, its superclass chain, then (instance only) its interfaces breadth-first; each once. */
    private List<String> candidates(String owner, boolean isStatic) {
        var seen = new LinkedHashSet<String>();
        var chain = new ArrayList<String>();
        for (String c = owner; c != null && seen.add(c); c = resolver.superclassOf(c)) {
            chain.add(c);
        }
        if (isStatic) {
            return chain;
        }
        var out = new ArrayList<>(chain);
        var queue = new ArrayDeque<String>();
        for (String c : chain) {
            queue.addAll(resolver.directInterfacesOf(c));
        }
        while (!queue.isEmpty()) {
            String itf = queue.poll();
            if (seen.add(itf)) {
                out.add(itf);
                queue.addAll(resolver.directInterfacesOf(itf));
            }
        }
        return out;
    }

    /**
     * The member a call from contract code binds to, or null: a member of the
     * owning contract class, a static of a helper class, an instance member
     * of the built-in {@code JArray} class (an {@link ContractMember.Kind#ARRAY_MODEL}
     * member; an instance call on any other helper misses), a {@code JVerify}
     * spec-interface method, otherwise the program lookups on the owner.
     */
    ContractMember lookupContractCode(String ownerInternal, String name, MethodTypeDesc desc,
                                      boolean isStatic) {
        if (disabled) {
            return null;
        }
        ensureLoaded();
        if (loadedBytes == null) {
            return null;
        }
        List<String> callParams = mappedParams(desc);
        var index = loadedBytes.index();
        ContractClass cc = index.contractsByInternal().get(ownerInternal);
        if (cc != null) {
            return contractMember(cc, name, isStatic, callParams);
        }
        if (index.helperClasses().contains(ownerInternal)) {
            ClassModel cm = classBytes(ownerInternal);
            String fqn = JavaNames.dottedFqn(ownerInternal);
            if (!isStatic) {
                return ownerInternal.equals(JARRAY_INTERNAL)
                        ? findMember(ContractMember.Kind.ARRAY_MODEL, cm, name, false, callParams,
                                ArrayModel.SORT, fqn, loadedBytes.classUris().get(ownerInternal), false)
                        : null;
            }
            return findMember(ContractMember.Kind.HELPER, cm, name, true, callParams,
                    fqn, fqn, loadedBytes.classUris().get(ownerInternal), false);
        }
        if (ownerInternal.startsWith(SPEC_INTERFACE_PREFIX)) {
            ClassModel cm = resolver.loadClassModel(ownerInternal.replace('/', '.'));
            if (cm == null) {
                return null;
            }
            String fqn = JavaNames.dottedFqn(ownerInternal);
            String sortOrFqn = isStatic ? fqn : TypeRef.ofClassInternal(ownerInternal, false, false).nameWithScope();
            return findMember(ContractMember.Kind.SPEC_INTERFACE, cm, name, isStatic, callParams,
                    sortOrFqn, fqn, null, false);
        }
        return isStatic ? lookupStatic(ownerInternal, name, desc) : lookupInstance(ownerInternal, name, desc);
    }

    /**
     * The instance member {@code name} of the built-in {@code JArray} class,
     * for array operations in programs, or null when the contracts are
     * disabled or not loaded, or JArray declares no single instance method of
     * that name. Its purity is recorded as for any other lookup.
     */
    ContractMember arrayMember(String name) {
        if (disabled) {
            return null;
        }
        ensureLoaded();
        ClassModel cm = classBytes(JARRAY_INTERNAL);
        if (cm == null) {
            return null;
        }
        var matches = Declarations.declaredMethodsOf(cm).stream()
                .filter(mm -> !isStatic(mm) && mm.methodName().stringValue().equals(name))
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        return findMember(ContractMember.Kind.ARRAY_MODEL, cm, name, false,
                mappedParams(matches.getFirst().methodTypeSymbol()), ArrayModel.SORT,
                JavaNames.dottedFqn(JARRAY_INTERNAL), loadedBytes.classUris().get(JARRAY_INTERNAL), false);
    }

    private ContractMember contractMember(ContractClass cc, String name, boolean isStatic,
                                          List<String> callParams) {
        ClassModel cm = classBytes(cc.contractInternal());
        String sortOrFqn = isStatic ? cc.contracteeFqn() : cc.sort();
        return findMember(ContractMember.Kind.CONTRACT, cm, name, isStatic, callParams,
                sortOrFqn, cc.contracteeFqn(), cc.uri(), cc.pure());
    }

    /** The declared method of {@code cm} matching name, staticness, and mapped parameters, as a member. */
    private ContractMember findMember(ContractMember.Kind kind, ClassModel cm, String name,
                                      boolean isStatic, List<String> callParams, String sortOrFqn,
                                      String contracteeFqn, URI uri, boolean contractPure) {
        var declared = Declarations.declaredMethodsOf(cm);
        for (var mm : declared) {
            if (mm.methodName().stringValue().equals(name) && isStatic(mm) == isStatic
                    && mappedParams(mm.methodTypeSymbol()).equals(callParams)) {
                var member = memberOf(kind, cm, mm, declared, sortOrFqn, contracteeFqn, uri, contractPure);
                pureKeys.put(member.sortOrFqn() + "_" + member.laurelName(), member.isPure());
                return member;
            }
        }
        return null;
    }

    private ContractMember memberOf(ContractMember.Kind kind, ClassModel cm, MethodModel mm,
                                    List<MethodModel> declared, String sortOrFqn, String contracteeFqn,
                                    URI uri, boolean contractPure) {
        String name = mm.methodName().stringValue();
        boolean isStatic = isStatic(mm);
        long sameName = declared.stream()
                .filter(x -> x.methodName().stringValue().equals(name) && isStatic(x) == isStatic)
                .count();
        String laurelName = name;
        if (sameName > 1) {
            var sb = new StringBuilder(name);
            for (var p : mm.methodTypeSymbol().parameterList()) {
                sb.append('?').append(erasedName(mapDesc(p)));
            }
            laurelName = sb.toString();
        }
        boolean spec = kind == ContractMember.Kind.SPEC_INTERFACE;
        boolean isPure = spec || Declarations.hasAnnotation(mm, "Pure");
        boolean verify = spec || effectiveVerify(cm, mm);

        boolean returnsTypeVar = false;
        int paramCount = mm.methodTypeSymbol().parameterCount();
        var paramIsTypeVar = new ArrayList<Boolean>();
        MethodSignature sig = mm.findAttribute(Attributes.signature())
                .map(sa -> MethodSignature.parseFrom(sa.signature().stringValue()))
                .orElse(null);
        if (sig != null) {
            returnsTypeVar = sig.result() instanceof Signature.TypeVarSig;
        }
        for (int i = 0; i < paramCount; i++) {
            paramIsTypeVar.add(sig != null && i < sig.arguments().size()
                    && sig.arguments().get(i) instanceof Signature.TypeVarSig);
        }
        return new ContractMember(kind, cm.thisClass().asInternalName(), name, mm.methodTypeSymbol(),
                sortOrFqn, laurelName, contracteeFqn, isStatic, isPure, mm.code().isPresent(), verify,
                returnsTypeVar, paramIsTypeVar, uri, contractPure);
    }

    /**
     * The effective {@code @Verify} of a library member: the class's
     * {@code overrideChildren} value wins, then the member's, then the
     * class's, else true ({@code verifyByDefault} never applies).
     */
    private static boolean effectiveVerify(ClassModel cm, MethodModel mm) {
        var classAnno = ClassResolver.verifyAnnotation(cm);
        var memberAnno = ClassResolver.verifyAnnotation(mm);
        if (classAnno != null && ClassResolver.verifyValue(classAnno, "overrideChildren", false)) {
            return ClassResolver.verifyValue(classAnno, "value", true);
        }
        if (memberAnno != null) {
            return ClassResolver.verifyValue(memberAnno, "value", true);
        }
        if (classAnno != null) {
            return ClassResolver.verifyValue(classAnno, "value", true);
        }
        return true;
    }

    private static boolean isStatic(MethodModel mm) {
        return (mm.flags().flagsMask() & ClassFile.ACC_STATIC) != 0;
    }

    /** A type as javac's erasure prints it: {@code int}, {@code java.lang.String}, {@code int[]}. */
    private static String erasedName(ClassDesc cd) {
        if (cd.isArray()) {
            return erasedName(cd.componentType()) + "[]";
        }
        if (cd.isPrimitive()) {
            return cd.displayName();
        }
        String d = cd.descriptorString();
        return JavaNames.dottedFqn(d.substring(1, d.length() - 1));
    }

    // --- Demand queue and purity ---

    /** Queue a member for translation; each {@link ContractMember#calleeKey()} is queued once. */
    void demand(ContractMember member) {
        if (demandedKeys.add(member.calleeKey())) {
            demandQueue.add(member);
        }
    }

    boolean hasDemand() {
        return !demandQueue.isEmpty();
    }

    /** The callee keys of every member demanded so far, in demand order. */
    List<String> demandedKeys() {
        return List.copyOf(demandedKeys);
    }

    /** The oldest queued member not yet handed out. */
    ContractMember nextDemand() {
        return demandQueue.poll();
    }

    /** True iff a member a lookup returned under {@code sortOrFqn_laurelName} is {@code @Pure}. */
    boolean isPureKey(String key) {
        return pureKeys.getOrDefault(key, false);
    }
}
