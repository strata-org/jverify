// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.6 -- Array Initializers: an array initializer {..} may be used in a
// declaration or array creation to create an array and provide initial values.
// Exercises: simple {..}, trailing comma, empty {}, arbitrary expression
// elements, nested initializers for multidimensional arrays, and that the
// length is determined by the number of variable initializers.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_6.ArrayInitializers_9#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayInitializers_9 {
    static void run() {

        // Simple array initializer in a declaration; length comes from the
        // number of variable initializers.
        int[] a = {10, 20, 30};

        // A trailing comma is permitted and ignored.
        int[] b = {1, 2, 3,};

        // Variable initializers may be arbitrary expressions.
        int x = 4;
        int[] c = {x, x + 1, x * 2, a[0] + b[0]};
        check(c[0] == 4);
    }
}
