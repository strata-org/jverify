// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_6;

import static org.strata.jverify.JVerify.*;

// JLS 10.6 -- Array Initializers used as the initializer of a class field.
class ArrayInitializers_Field {
    static int[] arr = {1, 2, 3};

    static void run() {
        check(arr[1] == 2);
    }
}
