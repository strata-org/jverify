package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code $result} is reserved by Strata (its {@code : T} return form generates
 * an output parameter of that name), and {@code $} is legal in a Java
 * identifier, so the frontend's naming cannot prevent a user binder of that
 * name. Such methods are refused for a graceful skip with an attributable
 * diagnostic; previously they aborted Strata with an unattributable type-check error
 * (pre-existing on the base commit for static methods too). Covers all four
 * binder sites: parameter, local, hoisted local, quantifier. Verified 2 =
 * clean + implicit constructor; skipped 4 = the refused methods.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 4, errorCount = 0)
final class ReservedResultName {

    static void viaParam(@Unbounded int $result) {
//              ^ error: identifier '$result' is reserved by the verifier
        check($result == $result);
    }

    @Unbounded
    static int viaLocal(@Unbounded int a) {
//             ^ error: identifier '$result' is reserved by the verifier
        @Unbounded int $result = a;
        check($result == a);
        return a;
    }

    static void viaHoistedLocal(@Unbounded int a) {
//              ^ error: identifier '$result' is reserved by the verifier
        @Unbounded int $result;
        if (a > 0) {
            $result = 1;
        } else {
            $result = 2;
        }
        check($result == $result);
    }

    static void viaQuantifier(@Unbounded int a) {
//              ^ error: identifier '$result' is reserved by the verifier
        check(forall((int $result) -> $result == $result));
    }

    @Unbounded
    static int clean(@Unbounded int a) {
        check(a == a);
        return a;
    }
}
