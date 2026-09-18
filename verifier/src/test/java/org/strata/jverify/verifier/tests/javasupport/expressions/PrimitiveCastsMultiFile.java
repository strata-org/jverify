package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Regression guard for the cast-helper deduplication.
 *
 * <p>Cast helpers ({@code java?cast_to_<T>_from_<S>}) are shared across all
 * Laurel files in one program scope, so if two files both use
 * e.g. {@code (short) someInt} the same helper must be emitted only once.
 * Before the fix the helper registry lived on the per-compilation-unit
 * {@code StaticMethodCollector} and every file with a cast wrote its own
 * copy, producing a {@code Duplicate definition 'java?cast_to_int16_from_int32'}
 * error at the synthetic (1:1-1:1) range.
 *
 * <p>This class casts int→short; the companion {@link PrimitiveCastsSecondFile}
 * (pulled in via {@code additionalFiles}) also casts int→short. Under the
 * previous behavior the two files together failed with a duplicate-definition
 * error; under the fix each cast still fails its own precondition but the
 * duplicate error is gone.
 */
@JVerifyTest(methodsVerified = 4, errorCount = 0, additionalFiles = {"./PrimitiveCastsSecondFile.java"})
class PrimitiveCastsMultiFile {
    static short castHere() {
        int x = 200;
        return (short) x;
    }
}
