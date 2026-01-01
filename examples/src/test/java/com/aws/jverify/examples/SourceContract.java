package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Impure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(javaVerified = 7, javaErrors = 0)
public class SourceContract {

    @Contract(Foo.class)
    static class FooContract implements Foo {
        int erasedValue;

        public int foo(int x) {
            precondition(x > 0);
            postcondition((int i) -> i >= i + 1);
            throw new ContractException();
        }

        public FooContract selfType() {
            postcondition((FooContract r) -> r.erasedValue == 3);
            throw new ContractException();
        }
    }
    
    static class User {
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

}
