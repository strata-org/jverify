package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 1)
public class Lambdas {

    public void useLambdas() {
        doSomethingTwice((x, y) -> x);
        doSomethingTwice((x, y) -> staticAdd(x, y));
        final int z = 42;
        doSomethingTwice((x, y) -> z);

        doSomethingTwice(this::add);
        doSomethingTwice(Lambdas::staticAdd);
        doSomethingwithSpecTwice((x, y) -> {
            precondition(x >= y);
            postcondition((Integer r) -> r == x - y);
            return x - y;
//                 ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
        });

        SomethingDoer doer = (x, y) -> {
            return x;
        };
        SomethingDoer doer2 = (x, y) -> {
            return x;
        };
        // Important that these values aren't equal,
        // since they aren't in Java semantics,
        // but if we map lambdas to datatype values incorrectly
        // they could be equal Dafny values.
        check(doer != doer2);
    }

    public void doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        var z = doer.doSomething(2, y);
    }

    public void doSomethingwithSpecTwice(SomethingDoerWithSpec doer) {
        var y = doer.doSomething(2, 1);
        var z = doer.doSomething(2, y);
    }

    public int add(int x, int y) {
        return x + y;
    }

    public static int staticAdd(int x, int y) {
        return x + y;
    }
}

interface SomethingDoer {
    int doSomething(int x, int y);
}

@Contract(SomethingDoer.class)
class SomethingDoerContract implements SomethingDoer {
    @Override
    public int doSomething(int x, int y) {
        throw new ContractException();
    }
}

interface SomethingDoerWithSpec {
    int doSomething(int x, int y);
}

@Contract(SomethingDoerWithSpec.class)
class SomethingDoerWithSpecContract implements SomethingDoerWithSpec {
    // TODO: Can't currently put @Nat on the return type because there's
    // currently no way to indicate that on the lambda expression
    // since it doesn't declare a return type anywhere.
    @Override
    public int doSomething(int x, int y) {
        precondition(x >= y);
        postcondition((Integer r) -> r == x - y);
        throw new ContractException();
    }
}