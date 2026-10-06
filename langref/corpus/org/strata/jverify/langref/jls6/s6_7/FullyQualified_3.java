package org.strata.jverify.langref.jls6.s6_7;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names: a member type nested inside another type
// (here `java.util.Map.Entry`) can be referenced by its dotted fully
// qualified name.
class FullyQualified_3 {
    static void run() {
        java.util.Map.Entry<Integer, String> e = java.util.Map.entry(1, "a");
        check(e.getKey() == 1);
    }
}
