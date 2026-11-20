package org.strata.jverify.verifier.tests.javasupport.packages;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.verifier.tests.javasupport.packages.samename.a.Foo;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, methodsVerified = 7, errorCount = 1, additionalFiles = {"./samename/b/Foo.java", "./samename/a/Foo.java"})
public class SameSimpleNameBA {
    static void u(Foo f) {
        int before = f.c;
        f.get();
        check(f.c == before);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void w(Foo f) {
        f.onlyA();
    }
}
