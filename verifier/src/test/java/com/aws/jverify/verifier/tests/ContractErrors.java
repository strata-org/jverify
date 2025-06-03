package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class ContractErrors {}

abstract class Foo {
    abstract void bar(int x);
}

@Contract(Foo.class)
class FooContract1 extends Foo {    
    void bar(int x) {
        check(x > 0);
    }

    void unusedExternalContract() {
//       ^ error: method 'unusedExternalContract' is part of a @Contract class, but it does not override any methods for which to provide a contract
        postcondition(false);
    }
}

   @Contract(Foo.class)
// ^ error: a class may not be referenced by more than one @Contract annotation
class FooContract2 extends Foo {
    void bar(int x) {
        check(x < 0);
    }
    
    @Contract(Foo.class)
//  ^ error: a class with @Contract may not be nested inside another type
    class NestedContractClass {
    }
}