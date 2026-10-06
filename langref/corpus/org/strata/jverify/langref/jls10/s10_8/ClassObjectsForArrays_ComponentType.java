// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: Class.getComponentType() returns the
// element type's Class object.
class ClassObjectsForArrays_ComponentType {
    static void run() {
        check(int[].class.getComponentType() == int.class);
    }
}
