package org.strata.jverify.langref.jls6.s6_7;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names: `java.util.ArrayList` may be referenced
// directly by its fully qualified name in place of an import declaration.
class FullyQualified_2 {
    static void run() {
        java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
        l.add(3);
        check(l.get(0) == 3);
    }
}
