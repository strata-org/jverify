package org.strata.jverify.langref.jls7.s7_5;

import static org.strata.jverify.JVerify.*;

import java.util.ArrayList;

// JLS 7.5.1 -- Single-Type-Import Declaration: imports a single named type so
// its simple name may be used within the compilation unit.
class SingleTypeImport_1 {
    static void run() {
        ArrayList<Integer> l = new ArrayList<>();
        l.add(1);
        check(l.get(0) == 1);
    }
}
