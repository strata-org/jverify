package com.aws.verifier.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;

import static com.aws.jverify.JVerify.*;

class Foo {
    public final static int c = 42;
    
    int foo(int x) {
        return x + 2;
    }
}

@Contract(Class.class)
public class ExternalContract {
    int foo(int x) {
        precondition(x > 0);
        postcondition((Integer i) -> i >= i + 1);
        throw new ContractException();
    }
}

class User {
    void test() {
        var foo = new Foo();
        var result = foo.foo(2);
        check(result > 2 + 1);
        check(Foo.c == 42);
    }
}