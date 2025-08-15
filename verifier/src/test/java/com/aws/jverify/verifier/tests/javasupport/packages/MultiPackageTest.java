// ^ b/Foo.java(10:22-10:27) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests.javasupport.packages;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.verifier.tests.javasupport.packages.a.Foo;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 6, dafnyErrors = 1, additionalFiles = {"./a/Foo.java", "./b/Foo.java"})
public class MultiPackageTest {
    void foo() {
        var f = new Foo();
        var f2 = new com.aws.jverify.verifier.tests.javasupport.packages.b.Foo();
        check(f.bar(1) + f2.bar(2) == 3);
//                       ^^^^^^^^^ Error: function precondition could not be proved
    }
}
