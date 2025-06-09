package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

interface Foo {
    public final static int c = 42;

    abstract int foo(int x);
}

@Contract(Foo.class)
@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class ExternalContract implements Foo {
    public final static int c = 43;
    
    public int foo(int x) {
        precondition(x > 0);
        postcondition((Integer i) -> i >= i + 1);
        throw new ContractException();
    }
}

class User {
    void test(Foo foo) {
        var result = foo.foo(2);
        check(result > 2 + 1);
        check(Foo.c == 43);
    }
}
