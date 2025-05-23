package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 0, dafnyErrors = 0)
public class NestedClass {
    @Nullable Nestee nestee;
    public class Nestee {
        
    }
}

class Nestee {
    @Nullable Nestee fakeNestee;
    @Nullable NestedClass.Nestee nestee;
}
