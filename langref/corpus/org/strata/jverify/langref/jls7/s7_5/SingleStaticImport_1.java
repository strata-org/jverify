package org.strata.jverify.langref.jls7.s7_5;

import static org.strata.jverify.JVerify.*;

import static java.lang.Math.PI;

// JLS 7.5.3 -- Single-Static-Import Declaration: imports a static member of a
// named type so it may be referred to by simple name.
class SingleStaticImport_1 {
    static void run() {
        check(PI > 3.14 && PI < 3.15);
    }
}
