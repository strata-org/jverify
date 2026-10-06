// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_2;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: array types are covariant, so String[] is
// assignable to Object[].
class ArrayVariables_Covariant {
    static void run() {
        Object[] o = new String[1];
        check(o.length == 1);
    }
}
