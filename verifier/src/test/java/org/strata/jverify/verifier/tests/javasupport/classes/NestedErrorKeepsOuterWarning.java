package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Regression for {@code hasReportedErrorInside}, which suppresses the
 * {@code instanceMethodNotVerified} warning when a hard error already names a
 * construct inside the SAME method.
 *
 * <p>A local class body sits inside its enclosing method's source span, so plain
 * range containment made an unrelated error in the local class swallow the outer
 * method's warning -- losing the only explanation for why {@code outer} was demoted
 * to Skipped. The check therefore attributes each error to the INNERMOST method
 * declaration around it, and only suppresses when that is the method being demoted.
 *
 * <p>Both diagnostics must appear: the local class's operator error, and the outer
 * method's demotion warning for an unrelated reason (a statement-group switch
 * expression, which is still untranslated -- the arrow form verifies).
 */
@JVerifyTest(exitCode = 2)
class NestedErrorKeepsOuterWarning {

    int outer(int i) {
        class Local {
            int masked(int x) {
                return x & 3;
//                       ^ error: operator &(int,int) is not supported
            }
        }
        var ret = switch (i) {
            case 0: yield 0;
            default: yield 1;
        };
        return ret;
    }
}
