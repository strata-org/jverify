package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaurelInterpreterFuelTest {

    @Test
    @Timeout(60)
    void unboundedRecursionRunsOutOfFuelByDefault(@TempDir Path dir) throws Exception {
        var file = dir.resolve("Rec.java");
        Files.writeString(file, """
                package p;
                import static org.strata.jverify.JVerify.*;
                class Rec {
                    static int recurse(int n) { return recurse(n + 1); }
                    static void run() { int r = recurse(0); check(r == 0); }
                }
                """);
        var out = new StringWriter();
        var commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(out, true));
        int exitCode = commandLine.execute("--interpret", "--interpreter", "laurel",
                "--entry", "p.Rec#run", file.toString());
        assertEquals(2, exitCode, out.toString());
        assertTrue(out.toString().contains("out of fuel"), out.toString());
    }
}
