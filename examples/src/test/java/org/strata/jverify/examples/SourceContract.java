package org.strata.jverify.examples;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/*
 * Verified 3 -> 2, Skipped +1: `User.test` is non-static so it now translates, and
 * its `foo.foo(2)` receiver is interface-typed. An implementation could override
 * `foo`, so the call is refused as not provably monomorphic; `test` skips rather
 * than verify against `Foo.foo`'s contract, which is not the one that would run.
 * The two contract methods still verify.
 */
@JVerifyTest(methodsVerified = 2, methodsSkipped = 1, errorCount = 0)
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
        // Not verified: interface-typed receiver, so the translator refuses the
        // polymorphic dispatch. methodsSkipped = 1 already counted this; the caret
        // below pins the warning and its reason.
        void test(Foo foo) {
//           ^ warning: instance method 'test' was not verified: polymorphic dispatch through interface/superclass is not yet supported
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
