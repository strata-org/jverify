// ^ b/WillVerify.java(10:9-10:21) Error: assertion might not hold
package com.aws.jverify.verifier.tests.shouldVerify;

import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 6,
        additionalFiles = {
        "./a/WontVerify.java", 
        "./a/package-info.java", 
        "./b/WillVerify.java", 
        "./b/package-info.java" }
)
public class ShouldVerify {}

@Verify(value = true, overrideChildren = true)
class ShouldVerify1 {
    
    @Verify(false)
    public ShouldVerify1() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
    
    @Verify(false)
    void foo1() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}

@Verify(value = false, overrideChildren = true)
class ShouldVerify2 {
    @Verify(true)
    void foo2() {
        check(false);
    }
}

@Verify(value = false, overrideChildren = false)
class ShouldVerify3 {
    @Verify(true)
    void foo3() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
    
    void foo4() {
        check(false);
    }
}

@Verify(value = true, overrideChildren = false)
class ShouldVerify4 {
    @Verify(true)
    void foo5() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }

    void foo6() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}