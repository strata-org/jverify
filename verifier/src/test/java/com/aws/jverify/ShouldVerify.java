package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@Verify(value = true, overrideChildren = true)
class ShouldVerify1 {
    
    @Verify(false)
    void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}

@Verify(value = false, overrideChildren = true)
class ShouldVerify2 {
    @Verify(true)
    void foo() {
        check(false);
    }
}

@Verify(value = false, overrideChildren = false)
class ShouldVerify3 {
    @Verify(true)
    void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
    
    void bar() {
        check(false);
    }
}

@Verify(value = true, overrideChildren = false)
class ShouldVerify4 {
    @Verify(true)
    void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }

    void bar() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}