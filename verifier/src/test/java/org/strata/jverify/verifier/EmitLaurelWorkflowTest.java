package org.strata.jverify.verifier;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;
import org.strata.jverify.testengine.JVerifyTestEngine;
import org.strata.jverify.verifier.laurel.FilesMap;
import org.strata.jverify.verifier.laurel.LaurelDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the {@code --emit-laurel} workflow, which separates
 * compilation (Java to the Laurel IR) from verification (Laurel IR to SMT via
 * Strata):
 *
 * <ol>
 *   <li>compile only — emit the serialized Laurel program to a file and assert
 *       that verification did <em>not</em> run; then</li>
 *   <li>verify separately — feed the emitted Laurel Ion to Strata on its own
 *       and assert it verifies.</li>
 * </ol>
 */
public class EmitLaurelWorkflowTest {

    @Test
    public void emitLaurelThenVerifySeparately() throws Exception {
        // A trivially-verifying program.
        String source = """
                import static org.strata.jverify.JVerify.*;
                class Emit {
                    static void trivial() {
                        check(1 == 1);
                    }
                }
                """;
        var sourceFile = new SourceFile(Path.of("Emit.java"), source);

        Path emittedLaurel = Files.createTempFile("emitted-laurel", ".ion");
        Files.deleteIfExists(emittedLaurel);

        var baseOptions =
                JVerifyTestEngine.getVerifierOptions(
                        JVerifyTestEngine.makeJVerifyTestAnnotation(1, 0), null);

        // ---- Step 1: compile only (emit Laurel, do NOT verify) ----
        var emitOptions = withEmit(baseOptions, emittedLaurel, /* emitLaurelOnly */ true);
        var emitDriver = Driver.getDriver(emitOptions);
        var emitResult = emitDriver
                .verifyJavaFiles(new java.util.ArrayList<>(List.of(sourceFile)));

        assertEquals(0, emitResult.exitCode(),
                "the emit-only run should succeed");
        assertNull(emitResult.verificationResults(),
                "the emit-only run must not invoke the backend / verify");
        assertTrue(Files.exists(emittedLaurel) && Files.size(emittedLaurel) > 0,
                "the serialized Laurel program should have been written");

        // ---- Step 2: verify the emitted Laurel Ion separately via Strata ----
        // Re-read the emitted file (rather than any in-memory compilation
        // output) and hand it to Strata: this is the separate
        // verification step the --emit-laurel workflow enables. The emit
        // driver is reused only because its context carries the
        // compilation metadata Strata's result mapping consults; the
        // program Strata verifies comes entirely from the emitted file.
        IonSystem ion = IonSystemBuilder.standard().build();
        IonValue emittedProgram = ion.singleValue(Files.readString(emittedLaurel));

        var laurelDriver = (LaurelDriver) emitDriver;
        // The program verifies cleanly, so no diagnostic positions are
        // produced and a trivial files map suffices.
        FilesMap trivialFilesMap = (uri, offset) -> new Position(1, 1);
        var verifyResult = laurelDriver.runVerifier(trivialFilesMap, emittedProgram);

        assertEquals(0, verifyResult.exitCode(),
                "verifying the separately-emitted Laurel Ion should succeed");
        assertNotNull(verifyResult.verificationResults(),
                "the separate verification run should produce verification results");
        assertEquals(0, verifyResult.verificationResults().verificationFailedAssertions(),
                "the trivially-true program should have no failed assertions");
    }

    private static VerifierOptions withEmit(VerifierOptions base, Path emit, boolean emitLaurelOnly) {
        return new VerifierOptions(
                base.outWriter(),
                base.workingDirectory(),
                base.backendPath(),
                base.extraClassPathEntries(),
                emit,
                emitLaurelOnly,
                base.showRanges(),
                base.contractSourcePath(),
                base.showFilepaths(),
                base.verifyByDefault(),
                base.continueOnErrors(),
                base.positionFilter(),
                base.verbose(),
                base.shouldTrackTime(),
                base.keepAllFilesDir());
    }
}
