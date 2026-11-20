package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class AlignUp {
    static int alignUp(@Nat int value, @Nat int align) {
        precondition(0 <= value && value <= 1000000);
        precondition(1 <= align && align <= 4096);
        postcondition((int res) -> value <= res && res % align == 0 && res - value < align);

        return ((value + align - 1) / align) * align;
    }
}
