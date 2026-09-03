package org.strata.java.bytecode.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.SourceFile;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier detection from classfile attribute evidence, one compilation per javac flag set. */
class CompilationFactsTest {

    private static final String SOURCE = """
            public class Probe {
                static int f(int x) {
                    int y = x + 1;
                    return y;
                }
            }
            """;

    private CompilationFacts factsOf(Path tmp, String dir, List<String> options) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        Path out = tmp.resolve(dir);
        Files.createDirectories(out);
        try (var fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            var src = new SourceFile(tmp.resolve("Probe.java"), SOURCE);
            assertTrue(compiler.getTask(new PrintWriter(new StringWriter()), fm, diagnostics,
                    options, null, List.of(src)).call(), () -> "javac failed: " + diagnostics.getDiagnostics());
        }
        var classes = BytecodeFrontend.readClasses(out);
        var facts = CompilationFacts.NONE_OBSERVED;
        for (var cls : classes) {
            facts = facts.or(CompilationFacts.of(cls.classModel()));
        }
        return facts;
    }

    @Test
    void fullTier(@TempDir Path tmp) throws Exception {
        var facts = factsOf(tmp, "full", List.of("-g", "-Xjcov", "-proc:none"));
        assertTrue(facts.hasCrt());
        assertTrue(facts.hasLocalVariableTable());
        assertEquals(CompilationFacts.Tier.FULL, facts.tier());
    }

    @Test
    void debugTier(@TempDir Path tmp) throws Exception {
        var facts = factsOf(tmp, "debug", List.of("-g", "-proc:none"));
        assertTrue(!facts.hasCrt());
        assertTrue(facts.hasLocalVariableTable());
        assertEquals(CompilationFacts.Tier.DEBUG, facts.tier());
    }

    @Test
    void bareTier(@TempDir Path tmp) throws Exception {
        // Default javac is -g:source,lines: line numbers survive, local names do not.
        var facts = factsOf(tmp, "bare", List.of("-proc:none"));
        assertTrue(!facts.hasCrt());
        assertTrue(!facts.hasLocalVariableTable());
        assertTrue(facts.hasLineNumberTable());
        assertEquals(CompilationFacts.Tier.BARE, facts.tier());
    }

    @Test
    void noneTier(@TempDir Path tmp) throws Exception {
        var facts = factsOf(tmp, "none", List.of("-g:none", "-proc:none"));
        assertEquals(CompilationFacts.Tier.NONE, facts.tier());
    }
}
