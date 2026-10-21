package org.strata.java.bytecode.verifier.laurel;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.VerificationResults;
import picocli.CommandLine;

import javax.tools.Diagnostic;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StrataBackendClassifyTest {

    @Test
    void strataKnownLimitationIsNotFailuresFound() {
        var uri = Paths.get("strata").toUri();
        var pos = new Position(3, 1);
        List<Diagnostic<?>> failures =
                List.of(new StrataDiagnostic(uri, new Range(pos, pos), "assertion does not hold"));
        var r = StrataBackend.classifyRun(4, failures,
                "Known limitation: 1 entry procedure(s) stopped at a call the interpreter refuses to run",
                uri, new VerificationResults(1, 0, 0, 0));
        assertInstanceOf(JVerifyResults.DidNotRun.class, r);
        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertEquals(failures, r.diagnostics());
    }
}
