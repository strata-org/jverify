package org.strata.jverify.verifier;

import org.strata.jverify.Nullable;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a verification run. A sealed hierarchy so the three genuinely
 * distinct states are not conflated (and illegal tuples are unrepresentable):
 * <ul>
 *   <li>{@link Ran} — the backend ran and produced a verdict ({@link VerificationResults}).</li>
 *   <li>{@link NoVerdict} — no verdict was produced by design (a usage error, or
 *       emit-Laurel-only mode); previously modelled as {@code null} results.</li>
 *   <li>{@link DidNotRun} — the backend failed to launch or aborted mid-run; not a
 *       verification outcome, so never read as a pass/fail. The test engine lets an
 *       xfail test absorb only Strata's program rejections (see {@code strataRejection});
 *       every other abort fails loud.</li>
 * </ul>
 * All variants carry {@code diagnostics} and an {@code exitCode}; only {@link Ran}
 * carries a {@link VerificationResults}, so a verdict cannot be read off a run that
 * did not produce one.
 */
public sealed interface JVerifyResults
        permits JVerifyResults.Ran, JVerifyResults.NoVerdict, JVerifyResults.DidNotRun {

    List<Diagnostic<?>> diagnostics();
    int exitCode();

    /**
     * The verdict if this run produced one ({@link Ran}), else {@code null} for a
     * run that produced none ({@link NoVerdict} / {@link DidNotRun}). Centralises
     * the "only Ran carries a verdict" unwrap so callers don't repeat the check.
     */
    default @Nullable VerificationResults verdictOrNull() {
        return this instanceof Ran ran ? ran.verificationResults() : null;
    }

    /** The backend ran and produced a verdict (never {@code null}). */
    record Ran(List<Diagnostic<?>> diagnostics, int exitCode,
               VerificationResults verificationResults) implements JVerifyResults {
        public Ran {
            Objects.requireNonNull(verificationResults,
                    "Ran must carry a verdict; use NoVerdict or DidNotRun when there is none");
        }
    }

    /** No verdict by design: a usage error, or emit-Laurel-only mode. */
    record NoVerdict(List<Diagnostic<?>> diagnostics, int exitCode) implements JVerifyResults {}

    /**
     * The backend failed to launch (exit -1) or aborted (non-zero exit).
     * {@code strataExitCode} is Strata's own exit code ({@code -1} if it never ran),
     * which {@code exitCode} may have remapped.
     */
    record DidNotRun(List<Diagnostic<?>> diagnostics, int exitCode, int strataExitCode) implements JVerifyResults {}
}
