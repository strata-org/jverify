package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest
public class NestedClass {
    Nestee nestee;
    public class Nestee {
        
    }
}

class Nestee {
    Nestee fakeNestee;
    NestedClass.Nestee nestee;
}
