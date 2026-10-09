package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JarFileEntry;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract library's load cache and jar contract sources: what the
 * {@code @JVerifyTest} corpus cannot observe. No Strata.
 */
class LibraryContractsTest {

    private static final URI OBJECT_CONTRACT_URI = URI.create("string:///object-contract.java");

    private static Path libraryJar() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private static List<Path> classPath() {
        return List.of(libraryJar());
    }

    private static ClassResolver resolver() {
        return new ClassResolver(classPath(), true);
    }

    private static MethodTypeDesc desc(String descriptor) {
        return MethodTypeDesc.ofDescriptor(descriptor);
    }

    /** Contracts over object-contract.java alone, bound with no program contract targets. */
    private static LibraryContracts objectOnly(Reporter reporter) {
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), classPath());
        contracts.bind(resolver(), Set.of(), reporter, null);
        return contracts;
    }

    private static LibraryContracts withUserSources(List<JavaFileObject> userSources, Reporter reporter,
                                                    URI anchor) {
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), userSources, classPath());
        contracts.bind(resolver(), Set.of(), reporter, anchor);
        return contracts;
    }

    private static List<JavaFileObject> userDir(Path dir) {
        return LibraryContracts.collectUserSources(Path.of(System.getProperty("user.dir")), List.of(dir));
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    /** "error: msg" / "warning: msg" texts, in report order. */
    private static List<String> messages(Reporter reporter) {
        return reporter.diagnostics().stream().map(d -> d.getMessage(null)).toList();
    }

    // --- Cache ---

    @Test
    void secondInstanceReusesCachedLoad() {
        var first = objectOnly(new Reporter());
        first.ensureLoaded();
        var second = objectOnly(new Reporter());
        second.ensureLoaded();
        assertNotNull(first.loadedBytes());
        assertSame(first.loadedBytes(), second.loadedBytes());
    }

    /** Contracts over {@code userSources}, compiled against the library jar plus {@code extra}. */
    private static LibraryContracts withClassPathEntry(List<JavaFileObject> userSources, Path extra,
                                                       Reporter reporter) {
        var cp = List.of(libraryJar(), extra);
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), userSources, cp);
        contracts.bind(new ClassResolver(cp, true), Set.of(), reporter, null);
        return contracts;
    }

    @Test
    void classPathContentChangeMissesTheCache(@TempDir Path tmp) throws Exception {
        Path cp = Files.createDirectories(tmp.resolve("cp"));
        var dep = BytecodeFrontend.compile(List.of(new SourceFile(tmp.resolve("Dep.java"),
                "package dep; public class Dep { public static int f() { return 1; } }")),
                classPath(), cp, Writer.nullWriter());
        assertTrue(dep.success(), () -> "javac failed: " + dep.diagnostics());
        write(tmp, "contracts/cc/UsesDep.java", """
                package cc;
                class UsesDep {
                    static int g() { return dep.Dep.f(); }
                }
                """);
        var sources = userDir(tmp.resolve("contracts"));

        var first = withClassPathEntry(sources, cp, new Reporter());
        first.ensureLoaded();
        assertTrue(first.loaded());
        var unchanged = withClassPathEntry(sources, cp, new Reporter());
        unchanged.ensureLoaded();
        assertSame(first.loadedBytes(), unchanged.loadedBytes());

        try (var walk = Files.walk(cp)) {
            for (var p : walk.sorted(java.util.Comparator.reverseOrder()).filter(p -> !p.equals(cp)).toList()) {
                Files.delete(p);
            }
        }
        var reporter = new Reporter();
        var afterDelete = withClassPathEntry(sources, cp, reporter);
        afterDelete.ensureLoaded();
        assertFalse(afterDelete.loaded());
        assertTrue(afterDelete.fatalLoadError());
        assertFalse(reporter.diagnostics().isEmpty());
        for (var message : messages(reporter)) {
            assertTrue(message.startsWith("error: Java compilation had an error: "), message);
        }
    }

    @Test
    void javacErrorInUserSourcesIsFatalAndNotCached(@TempDir Path tmp) throws Exception {
        write(tmp, "p/Broken.java", """
                package p;
                class Broken {
                    int x = ;
                }
                """);
        var sources = userDir(tmp);
        for (int run = 0; run < 2; run++) {
            var reporter = new Reporter();
            var contracts = withUserSources(sources, reporter, null);
            contracts.ensureLoaded();
            assertFalse(reporter.diagnostics().isEmpty());
            for (var d : reporter.diagnostics()) {
                assertEquals(Diagnostic.Kind.ERROR, d.getKind());
                assertTrue(d.getMessage(null).startsWith("error: Java compilation had an error: "),
                        d.getMessage(null));
                assertEquals(URI.create("string://p/Broken.java"), ((StrataDiagnostic) d).getUri());
                assertEquals(3, ((DiagnosticWithRange) d).getRange().start().line());
            }
            assertTrue(contracts.fatalLoadError());
            assertFalse(contracts.loaded());
            assertNull(contracts.lookupInstance("java/lang/String", "length", desc("()I")));
        }
    }

    // --- Jar contract sources ---

    @Test
    void jarContractSource(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("contracts.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("q/MathContract.java"));
            out.write("""
                    package q;
                    import org.strata.jverify.Contract;
                    import org.strata.jverify.ContractException;
                    import org.strata.jverify.Pure;
                    @Contract(Math.class)
                    class MathContract {
                        @Pure
                        public static int abs(int a) {
                            throw new ContractException();
                        }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("q/README.txt"));
            out.write("not a source".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        var sources = LibraryContracts.collectUserSources(tmp, List.of(Path.of("contracts.jar")));
        assertEquals(1, sources.size());
        assertInstanceOf(JarFileEntry.class, sources.getFirst());

        var reporter = new Reporter();
        var contracts = withUserSources(sources, reporter, null);
        contracts.ensureLoaded();
        assertEquals(List.of(), messages(reporter));
        assertTrue(contracts.loadedBytes().index().contractsByContractee().containsKey("java.lang.Math"));
        var abs = contracts.lookupStatic("java/lang/Math", "abs", desc("(I)I"));
        assertNotNull(abs);
        assertEquals("java.lang.Math", abs.sortOrFqn());
        assertEquals("abs", abs.laurelName());
        URI expected = URI.create("jar:" + jar.toUri() + "!/q/MathContract.java");
        assertEquals(expected, abs.uri());
        assertTrue(abs.uri().toString().startsWith("jar:file:"));
        assertEquals(List.of(OBJECT_CONTRACT_URI, expected), contracts.loadedUris());
    }
}
