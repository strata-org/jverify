// ^ b/Foo.java(11:22-11:27) Related location: this proposition could not be proved
// ^ b/Foo.java(16:9-16:21) Error: assertion could not be proved
package com.aws.jverify.verifier.tests.javasupport.packages;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.verifier.tests.javasupport.packages.a.Foo;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 8, dafnyErrors = 3, additionalFiles = {"./a/Foo.java", "./b/Foo.java"})
public class MultiPackageTest {
    void foo() {
        var f = new Foo();
        var f2 = new com.aws.jverify.verifier.tests.javasupport.packages.b.Foo();
        check(f.bar(1) + f2.bar(2) == 3);
//                       ^^^^^^^^^ Error: function precondition could not be proved
    }

    /**
     * useful for filter position test
     */
    void bar() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
