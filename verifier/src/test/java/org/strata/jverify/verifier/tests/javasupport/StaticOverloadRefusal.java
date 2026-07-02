package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Overloaded methods all mangle to the same flat {@code Class_name} Laurel
 * procedure name, so they collide. Until overload disambiguation is supported,
 * such methods are refused with a clear diagnostic and flipped to Skipped
 * rather than silently producing colliding procedures. The non-overloaded
 * {@code unique} method still verifies. The verified count is 2 — {@code unique}
 * plus the synthetic default constructor — matching the convention in
 * {@code TranslatorSkip}.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2,
        methodsSkipped = 2,
        errorCount = 0
)
class StaticOverloadRefusal {

    @Pure
    static int dup(int x) {
//             ^ error: overloaded method 'dup' (enclosing class declares 2 methods with this name); overloading is not supported
        return x;
    }

    @Pure
    static int dup(boolean b) {
//             ^ error: overloaded method 'dup' (enclosing class declares 2 methods with this name); overloading is not supported
        return b ? 1 : 0;
    }

    @Pure
    static int unique(int x) {
        postcondition((int r) -> r == x);
        return x;
    }
}
