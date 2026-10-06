// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation for a reference component type initializes each
// component to the default value null.
class ArrayCreation_RefDefaultsNull {
    static void run() {
        String[] a = new String[3];
        check(a[0] == null && a[2] == null);
    }
}
