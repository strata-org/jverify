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
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_6.ArrayInitializers_21#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayInitializers_21 {
    static void run() {

        // Initializer for an array of references (String) with a null element.
        String[] s = {"a", null, "c"};
        check(s.length == 3);
    }
}
