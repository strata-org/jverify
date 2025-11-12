package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Impure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@Contract(Foo.class)
@JVerifyTest(dafnyVerified = 5, dafnyErrors = 0)
public class SourceContract implements Foo {
    int erasedValue;
    
    public int foo(int x) {
        precondition(x > 0);
        postcondition((int i) -> i >= i + 1);
        throw new ContractException();
    }
    
    public SourceContract selfType() {
      postcondition((SourceContract r) -> r.erasedValue == 3);
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

@Impure
interface Foo {
    public final static int c = 42;

    abstract int foo(int x);

    public Foo selfType();
}
