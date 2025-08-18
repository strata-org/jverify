package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Reference;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@Contract(Foo.class)
@JVerifyTest(exitCode = 0, dafnyVerified = 5, dafnyErrors = 0)
public class ExternalContract implements Foo {
    int erasedValue;
    
    public int foo(int x) {
        precondition(x > 0);
        postcondition((int i) -> i >= i + 1);
        throw new ContractException();
    }
    
    public ExternalContract selfType() {
      postcondition((ExternalContract r) -> r.erasedValue == 3);
      erasedValue = 3;
      return this;
    }
}

class User {
    void test(Foo foo) {
        var result = foo.foo(2);
        check(result > 2 + 1);
        check(Foo.c == 43);
    }
}

@Reference
interface Foo {
    public final static int c = 42;

    abstract int foo(int x);

    public Foo selfType();
}
