package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #447 soundness backstop: a non-zero exit code must never be reported as a
 * vacuous "Found 0 errors". Drives the real verifyJavaFilesExit ->
 * outputVerificationResults path with a stub Driver returning a canned
 * JVerifyResults, asserting on the reported error count.
 */
class NonZeroExitReportingTest {

    private static final Pattern FOUND_ERRORS = Pattern.compile("Found (\\d+) errors");

    private static int reportedErrorCount(JVerifyResults canned) throws Exception {
        var out = new StringWriter();
        var base = JVerifyTestEngine.getVerifierOptions(
                JVerifyTestEngine.makeJVerifyTestAnnotation(0, 0), null);
        var options = new VerifierOptions(new PrintWriter(out),
                base.workingDirectory(), base.backendPath(), base.extraClassPathEntries(),
                base.printSerializedOutputProgram(), base.emitLaurelOnly(), base.showRanges(),
                base.contractSourcePath(), base.showFilepaths(), base.verifyByDefault(),
                base.continueOnErrors(), base.positionFilter(), base.verbose(), base.shouldTrackTime());
        Driver driver = new Driver() {
            @Override public VerifierOptions getVerifierOptions() { return options; }
            @Override public JVerifyResults verifyJavaFiles(List<JavaFileObject> readFiles) { return canned; }
        };
        int exit = driver.verifyJavaFilesExit(List.of());
        assertEquals(canned.exitCode(), exit, "exit code must pass through unchanged");
        Matcher m = FOUND_ERRORS.matcher(out.toString());
        assertTrue(m.find(), "report must contain a 'Found N errors' line, got:\n" + out);
        return Integer.parseInt(m.group(1));
    }

    @Test
    void nonZeroExitWithNoDiagnosticsReportsAtLeastOneError() throws Exception {
        assertTrue(reportedErrorCount(new JVerifyResults(List.of(), -1, null)) >= 1,
                "a non-zero exit with no diagnostics must report at least one error, not a vacuous 0");
    }

    @Test
    void zeroExitWithNoErrorsStillReportsZero() throws Exception {
        assertEquals(0, reportedErrorCount(new JVerifyResults(List.of(), 0,
                new VerificationResults(3, 0, 0, 0))));
    }
}
