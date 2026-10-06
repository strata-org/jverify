// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_6;

import static org.strata.jverify.JVerify.*;

// JLS 10.6 -- Array Initializers: each initializer expression is subject to
// assignment conversion, so int literals widen to long in a long[].
class ArrayInitializers_WideningInt {
    static void run() {
        long[] a = {1, 2};
        check(a[1] == 2L);
    }
}
