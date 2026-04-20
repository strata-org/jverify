// ^ b/Foo.java(11:22-11:27) Related location: this proposition could not be proved
// ^ b/Foo.java(16:9-16:21) Error: assertion does not hold
package org.strata.jverify.verifier.tests.javasupport.packages;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.verifier.tests.javasupport.packages.a.Foo;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 5, errorCount = 3, additionalFiles = {"./a/Foo.java", "./b/Foo.java"})
public class MultiPackageTest {
    void foo() {
        var f = new Foo();
        var f2 = new org.strata.jverify.verifier.tests.javasupport.packages.b.Foo();
        check(f.bar(1) + f2.bar(2) == 3);
//                       ^^^^^^^^^ Error: function precondition could not be proved
    }

    /**
     * useful for filter position test
     */
    void bar() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}
