package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the JavaToLaurelCompiler behaviour when Strata reports a diagnostic
 * against the synthetic {@code <unknown>} URI (a node with no source in our
 * line map): it must (1) surface as a user-visible Verifier error rather than
 * crashing with "Could not find line map", and (2) be flagged LOUDLY as a
 * synthesis bug rather than silently degrading to a misleading {@code 1:1}.
 *
 * <p>The trigger is a parameter named {@code result}: JVerify renames the
 * postcondition lambda's parameter to Strata's canonical {@code result}, so it
 * collides with Strata's implicit {@code result} <em>return</em> binding and
 * Strata reports "Duplicate definition 'result'". That diagnostic lands on the
 * return binding — a node JVerify does not construct, so it has no source even
 * though JVerify now threads source ranges into everything it <em>does</em>
 * synthesize (parameters, return types, requires/ensures/invariant clauses).
 * Hence this case exercises the residual, now-loud fallback.
 *
 * <p>Note: the underlying collision is removed by #431 (which renames the user
 * parameter), so this test relies on pre-#431 behaviour to reach the
 * synthetic-URI path.
 */
public class UnknownLineMapFallbackTest {

    @Test
    public void syntheticUnknownUriSurfacesDiagnosticLoudlyInsteadOfCrashing() throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                class ResultParam {
                    static int identity(int result) {
                        postcondition((int r) -> r == result);
                        return result;
                    }
                }
                """;
        var sourceFile = new SourceFile(Path.of("ResultParam.java"), source);
        var options = JVerifyTestEngine.getVerifierOptions(
                JVerifyTestEngine.makeJVerifyTestAnnotation(0, 0), null);

        // Capture stderr to assert the fallback flags the synthesis bug loudly.
        var captured = new ByteArrayOutputStream();
        var savedErr = System.err;
        JVerifyResults results;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            // Without the fallback the synthetic <unknown> URI has no line map
            // and this throws "Could not find line map"; with it, the Strata
            // diagnostic threads through instead.
            results = Driver.getDriver(options)
                    .verifyJavaFiles(new ArrayList<>(List.of(sourceFile)));
        } finally {
            System.setErr(savedErr);
        }

        assertNotEquals(0, results.exitCode(),
                "the result-parameter collision should surface as a Verifier error");
        assertFalse(results.diagnostics().isEmpty(),
                "a user-visible diagnostic should be reported, not masked by a crash");
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("source-less synthesized node"),
                "the residual synthetic-URI fallback must be flagged loudly as a synthesis bug, "
                        + "not silently degraded to a 1:1 location");
    }
}
