package org.strata.jverify.verifier.tests.examples;

import static org.strata.jverify.JVerify.*;

// Helper class with same method name as NameCollision to test cross-class collision
class NameCollisionHelper {
    static int compute(int x) {
        precondition(0 <= x && x <= 1000);
        return x + 1;
    }
}
