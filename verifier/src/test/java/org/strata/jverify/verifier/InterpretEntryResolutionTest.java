package org.strata.jverify.verifier;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpretEntryResolutionTest {

    private record Run(int exitCode, String output) {}

    private static Run interpret(Path file, String interpreter, String entry) {
        var out = new StringWriter();
        var commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(out, true));
        int exitCode = commandLine.execute("--interpret", "--interpreter", interpreter,
                "--entry", entry, file.toString());
        return new Run(exitCode, out.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"core", "laurel"})
    void entriesThatNormalizeAlikeRunTheirOwnMethod(String interpreter, @TempDir Path dir) throws Exception {
        var file = dir.resolve("A.java");
        Files.writeString(file, """
                package p;
                import static org.strata.jverify.JVerify.*;
                class A { static void b_c() { check(false); } }
                class A_b { static void c() { check(true); } }
                """);
        assertEquals(0, interpret(file, interpreter, "p.A_b#c").exitCode());
        var run = interpret(file, interpreter, "p.A#b_c");
        assertEquals(4, run.exitCode(), run.output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"core", "laurel"})
    void methodNamedStaticSomethingResolves(String interpreter, @TempDir Path dir) throws Exception {
        var file = dir.resolve("StaticPrefix.java");
        Files.writeString(file, """
                package p;
                import static org.strata.jverify.JVerify.*;
                class StaticPrefix { static void staticHelper() { check(1 + 1 == 2); } }
                """);
        var run = interpret(file, interpreter, "p.StaticPrefix#staticHelper");
        assertEquals(0, run.exitCode(), run.output());
    }
}
