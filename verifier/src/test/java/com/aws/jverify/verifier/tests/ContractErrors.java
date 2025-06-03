package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class ContractErrors {}

class Foo {
//    ^ error: class 'Foo' may not be verified because it has an externally defined contract 
    void bar(int x) {}
}

@Contract(Foo.class)
class FooContract1 {    
    void bar(int x) {
        check(x > 0);
    }

    void unusedExternalContract() {
//       ^ error
        postcondition(false);
    }
}

   @Contract(Foo.class)
// ^ error: a class may not be referenced by more than one @Contract annotation
class FooContract2 {
    void bar(int x) {
        check(x < 0);
    }
    
    @Contract(Foo.class)
//  ^ error: a class with @Contract may not be nested inside another type
    class NestedContractClass {
    }
}