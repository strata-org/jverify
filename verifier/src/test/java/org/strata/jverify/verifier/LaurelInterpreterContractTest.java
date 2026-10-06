package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaurelInterpreterContractTest {

    private record Run(int exitCode, String output) {}

    private static Run interpret(Path dir, String className, String source) throws Exception {
        var file = dir.resolve(className + ".java");
        Files.writeString(file, source);
        var out = new StringWriter();
        var commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(out, true));
        int exitCode = commandLine.execute("--interpret", "--interpreter", "laurel",
                "--entry", "p." + className + "#run", file.toString());
        return new Run(exitCode, out.toString());
    }

    @Test
    void violatedStaticPostconditionIsReported(@TempDir Path dir) throws Exception {
        var run = interpret(dir, "StaticPost", """
                package p;
                import static org.strata.jverify.JVerify.*;
                class StaticPost {
                    static int f(int x) { postcondition((int r) -> r > 100); return x; }
                    static void run() { int r = f(1); }
                }
                """);
        assertEquals(4, run.exitCode(), run.output());
        assertTrue(run.output().contains("postcondition does not hold"), run.output());
    }

    @Test
    void violatedInstancePostconditionIsReported(@TempDir Path dir) throws Exception {
        var run = interpret(dir, "InstancePost", """
                package p;
                import static org.strata.jverify.JVerify.*;
                class InstancePost {
                    int get(int x) { postcondition((int r) -> r > 100); return x; }
                    static void run() { InstancePost b = new InstancePost(); int r = b.get(1); }
                }
                """);
        assertEquals(4, run.exitCode(), run.output());
        assertTrue(run.output().contains("postcondition does not hold"), run.output());
    }

    @Test
    void violatedPreconditionIsReported(@TempDir Path dir) throws Exception {
        var run = interpret(dir, "Pre", """
                package p;
                import static org.strata.jverify.JVerify.*;
                class Pre {
                    static int f(int x) { precondition(x > 0); return x; }
                    static void run() { int r = f(0); }
                }
                """);
        assertEquals(4, run.exitCode(), run.output());
        assertTrue(run.output().contains("precondition does not hold"), run.output());
    }

    @Test
    void satisfiedContractsRunCleanly(@TempDir Path dir) throws Exception {
        var run = interpret(dir, "Ok", """
                package p;
                import static org.strata.jverify.JVerify.*;
                class Ok {
                    static int f(int x) { precondition(x > 0); postcondition((int r) -> r > x); return x + 1; }
                    static void run() { check(f(1) == 2); }
                }
                """);
        assertEquals(0, run.exitCode(), run.output());
    }
}
