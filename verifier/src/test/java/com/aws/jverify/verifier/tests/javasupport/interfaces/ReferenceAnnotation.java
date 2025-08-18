package com.aws.jverify.verifier.tests.javasupport.interfaces;

import com.aws.jverify.Contract;
import com.aws.jverify.Reference;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 22)
public class ReferenceAnnotation {

    interface Immutable {
        void increment();
        int getX();

        @Contract
        class MyContract implements Immutable {
            int x;

            public void increment() {
                modifies(this); // error
//                       ^^^^ Error: a modifies-clause expression must denote an object, a single field location like o`x or a`[i] of type (object, field)  (with `--referrers`), or a set/iset/multiset/seq of objects or single field locations (with `--referrers`) (instead got Immutable)
                postcondition(x == old(x) + 1);
            }

            @Pure
            public int getX() {
                return x;
            }
        }
    }
    
    @Reference
    interface Mutable {
        void increment();
        int getX();

        @Contract
        class MyContract implements Mutable {
            int x;

            public void increment() {
                modifies(this);
                postcondition(x == old(x) + 1);
            }

            @Pure
            public int getX() { return x; }
        }
    }
}
