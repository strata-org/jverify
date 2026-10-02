package org.strata.jverify.verifier.laurel;

public final class StrataOutput {
    private static final String VC_SECTION_HEADER = "VCs:";

    private StrataOutput() {
    }

    public static boolean reachedVerificationConditions(String preDiagnosticOutput) {
        return preDiagnosticOutput.lines().anyMatch(VC_SECTION_HEADER::equals);
    }
}
