// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_7;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: arrays inherit Object.equals, which is reference
// identity; two distinct arrays with equal contents are not equal.
class ArrayMembers_EqualsIsIdentity {
    static void run() {
        int[] a = {1};
        int[] b = {1};
        check(!a.equals(b));
    }
}
