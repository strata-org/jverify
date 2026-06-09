package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Locks the JavaToLaurelCompiler line-map fallback for the synthetic
 * {@code <unknown>} URI: a Strata diagnostic reported against it must surface
 * as a user-visible Verifier error rather than crashing with
 * "Could not find line map".
 *
 * <p>The trigger is a parameter named {@code result}: JVerify renames the
 * postcondition lambda's parameter to Strata's canonical {@code result}, so
 * both bind {@code result} in the same scope and Strata reports a
 * "Duplicate definition 'result'" diagnostic — against the synthetic
 * {@code <unknown>} URI, which has no line-map entry.
 *
 * <p>Note: the underlying collision is fixed by #431 (which renames the user
 * parameter), so this test relies on the pre-#431 behaviour to exercise the
 * synthetic-URI fallback path. A marked-source {@code @JVerifyTest} resource
 * is a poor fit here because the diagnostic lands at the synthetic position
 * {@code 1:1}, which positional markup can't express cleanly.
 */
public class UnknownLineMapFallbackTest {

    @Test
    public void syntheticUnknownUriSurfacesDiagnosticInsteadOfCrashing() throws Exception {
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

        // Without the fallback, the synthetic <unknown> URI has no line map
        // and this throws "Could not find line map"; with it, the Strata
        // diagnostic threads through instead.
        var results = Driver.getDriver(options)
                .verifyJavaFiles(new ArrayList<>(List.of(sourceFile)));

        assertNotEquals(0, results.exitCode(),
                "the result-parameter collision should surface as a Verifier error");
        assertFalse(results.diagnostics().isEmpty(),
                "a user-visible diagnostic should be reported, not masked by a crash");
    }
}
