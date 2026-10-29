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
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_6.ArrayInitializers_24#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayInitializers_24 {
    static void run() {

        // boolean array initializer.
        boolean[] flags = {true, false, true};
        check(flags[0]);
    }
}
