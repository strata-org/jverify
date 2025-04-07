package com.aws.verifier.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Verify;

import static com.aws.jverify.JVerify.*;

@Verify(value = false)
class Foo {
    public final static int c = 42;
    
    int foo(int x) {
        return x + 2;
    }
}

@Contract(Foo.class)
public class ExternalContract {
    public final static int c = 43;
    
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
        check(Foo.c == 43);
    }
}