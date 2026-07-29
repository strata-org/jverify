package org.strata.jverify.verifier.tests.interpret;

// Helper for InterpretCrossFile: lives in a separate file so the interpret run
// combines multiple inputs. Deliberately carries no failing contract of its own
// — the only diagnostic must come from the marked file, which is what pins the
// per-file offset-to-position mapping.
class InterpretCrossFileHelper {
    static int doubleIt(int x) {
        return x * 2;
    }
}
