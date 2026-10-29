package org.strata.java.bytecode.testengine;

import org.junit.jupiter.api.Test;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;
import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.JVerifyTestRecord;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.jverify.verifier.StrataTimeout;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultGuardsTest {

    private static final SourceFile SRC = new SourceFile(Path.of("Probe.java"), "class Probe {}");

    private static JVerifyTest interpretAnnotation(boolean interpret) {
        return new JVerifyTestRecord("", true, false, false, 0, new String[0], new String[0],
                -1, -1, -1, -1, false, new Blocker[0], interpret, "Probe#run", -1, "laurel");
    }

    private static Diagnostic<?> diagnostic(String message) {
        return new Diagnostic<Object>() {
            public Kind getKind() { return Kind.ERROR; }
            public Object getSource() { return null; }
            public long getPosition() { return NOPOS; }
            public long getStartPosition() { return NOPOS; }
            public long getEndPosition() { return NOPOS; }
            public long getLineNumber() { return 1; }
            public long getColumnNumber() { return 1; }
            public String getCode() { return null; }
            public String getMessage(Locale locale) { return message; }
        };
    }

    private static JVerifyResults.DidNotRun abortedWith(String message, int exitCode) {
        return new JVerifyResults.DidNotRun(List.of(diagnostic(message)), exitCode, exitCode);
    }

    private static String abort(int strataExitCode, String output) {
        return "verification aborted: Strata exited with code " + strataExitCode
                + " without reporting diagnostics: " + output;
    }

    private static void assertRejection(JVerifyResults results) {
        assertThrows(BytecodeTestEngine.StrataRejection.class,
                () -> BytecodeTestEngine.checkResults(results, SRC, interpretAnnotation(true),
                        List.<AnnotatedRange>of()));
    }

    private static void assertLoud(JVerifyResults results) {
        assertThrows(IllegalStateException.class,
                () -> BytecodeTestEngine.checkResults(results, SRC, interpretAnnotation(true),
                        List.<AnnotatedRange>of()));
    }

    private static final String REJECTED = abort(1, "Exception: Laurel resolution failed: #[expected 'String', "
            + "got 'string']\n\nRun strataLaurel --help for additional help.");

    @Test
    void strataRejectingAnInterpretedProgramIsASwallowableFailure() {
        assertRejection(abortedWith(REJECTED, 1));
    }

    @Test
    void eachProgramRejectionMessageIsASwallowableFailure() {
        for (var rejection : BytecodeTestEngine.STRATA_PROGRAM_REJECTIONS) {
            assertRejection(abortedWith(abort(1, "Exception: " + rejection + ": x"), 1));
        }
    }

    @Test
    void aStrataUsageErrorStaysLoud() {
        assertLoud(abortedWith(abort(1, "Exception: Invalid interpreter: 'x' (expected 'core' or 'laurel')"
                + "\n\nRun strataLaurel --help for additional help."), 1));
    }

    @Test
    void aMissingEntryPointStaysLoud() {
        assertLoud(abortedWith(abort(1, "Exception: no entry point found: mark a procedure with `entry`"
                + "\n\nRun strataLaurel --help for additional help."), 1));
    }

    @Test
    void aStrataInternalErrorStaysLoud() {
        assertLoud(abortedWith(abort(3, "Exception: Laurel resolution failed: internal"), 3));
    }

    @Test
    void strataFindingFailuresIsASwallowableFailure() {
        assertRejection(abortedWith(abort(2, "Failures found: 1 entry procedure(s) ended with an interpreter error"),
                2));
    }

    @Test
    void aKnownLimitationAfterADiagnosticIsASwallowableFailure() {
        assertRejection(new JVerifyResults.DidNotRun(
                List.of(diagnostic("assertion does not hold")), 2, 4));
    }

    @Test
    void anExecFailureOfAWrapperStaysLoud() {
        assertLoud(abortedWith(abort(127, "sh: strataLaurel: not found"), 127));
    }

    @Test
    void aStrataKilledBySignalStaysLoud() {
        assertLoud(abortedWith(abort(128, ""), 128));
        assertLoud(abortedWith(abort(137, ""), 137));
    }

    @Test
    void aStrataLaunchFailureOfAnInterpretRunStaysLoud() {
        assertLoud(new JVerifyResults.DidNotRun(List.of(), -1, -1));
    }

    @Test
    void aTimedOutInterpretRunStaysLoudAndSaysSo() {
        var reason = StrataTimeout.MESSAGE_PREFIX + "1 s (" + StrataTimeout.PROPERTY + ")";
        var pos = new Position(1, 1);
        var timedOut = new StrataDiagnostic(SRC.toUri(), new Range(pos, pos), reason);
        for (var strataExit : List.of(1, 2, 4, 137)) {
            var e = assertThrows(IllegalStateException.class,
                    () -> BytecodeTestEngine.checkResults(new JVerifyResults.DidNotRun(
                                    List.of(diagnostic(abort(strataExit, "Laurel resolution failed")), timedOut),
                                    strataExit, strataExit),
                            SRC, interpretAnnotation(true), List.<AnnotatedRange>of()));
            assertTrue(e.getMessage().contains(": " + reason + "; "), e.getMessage());
        }
    }

    @Test
    void aStrataPanicStaysLoud() {
        assertLoud(abortedWith(abort(4, "PANIC at Lean.Expr.bindingBody!"), 2));
    }

    @Test
    void aLowercaseStrataPanicStaysLoud() {
        assertLoud(abortedWith(abort(2, "panic: unreachable code reached"), 2));
    }

    @Test
    void strataRejectionOutsideInterpretModeStaysLoud() {
        assertThrows(IllegalStateException.class,
                () -> BytecodeTestEngine.checkResults(abortedWith(REJECTED, 1), SRC, interpretAnnotation(false),
                        List.<AnnotatedRange>of()));
    }
}
