package com.aws.jverify.verifier.tests.multipackage;

import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.verifier.tests.multipackage.a.Foo;

import java.nio.file.Path;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(additionalFiles = {"./a/Foo.java", "./b/Foo.java"})
public class MultiPackageTest {
    void foo() {
        var f = new Foo();
        var f2 = new com.aws.jverify.verifier.tests.multipackage.b.Foo();
        check(f.bar() + f2.bar() == 3);
    }
}
