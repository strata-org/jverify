package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
// Three methods are skipped -- P2, P and ignoreCallResult (each caret-pinned below).
// Every one calls an instance method on implicit `this`, and since neither the callee
// nor this class is final, a subclass could override it: a possibly-polymorphic call,
// which the translator refuses. @Pure is irrelevant here -- ignoreCallResult is not
// @Pure and its callee methodWithResult is a plain instance method -- and so is being
// called: @Pure P3 calls nothing and verifies. Making a callee (or the class) final,
// or static, would translate these three, at the cost of the coverage.
@JVerifyTest(methodsVerified = 10, methodsSkipped = 3, errorCount = 0)
class VerifyStatements {
    void forLoop() {
        int i = 0;
        for(i = 0; i < 5; i = i + 1) {
            invariant(i >= 0 && i <= 5);
        }
        check(i == 5);
    }

    void nestedForLoop() {
        int x = 0;
        for (int i = 0; i < 5; i = i + 1) {
            invariant(x == i * 5 && i >= 0 && i <= 5);
            for (int j = 0; j < 5; j = j + 1) {
                invariant(x == j + i * 5 && j >= 0 && j <= 5);
                x = x + 1;
            }
        }
        check(x == 25);
    }
    
    void nestedForLoopContinue() {
        int x = 0;
        outerLoop:
        for (int i = 0; i < 5; i = i + 1) {
            invariant(i <= 2 ? x == i * 5 : x == 12 + (i - 3) * 5);
            invariant(i >= 0 && i <= 5);
            
            for (int j = 0; j < 5; j = j + 1) {
                invariant(i <= 2 ? x == j + i * 5 : x == j + 12 + (i - 3) * 5);
                invariant(j >= 0 && j <= 5);
                invariant(i == 2 ? j <= 2 : true);
                if (i == 2 && j == 2) {
                    check(x == 12);
                    continue outerLoop;
                }
                x = x + 1;
            }
        }
    }

    void doWhileLoop() {
        int x = 0;
        do {
            decreases(5 - x);
            invariant(x >= 0 && x <= 4);
            x = x + 1;
        } while(x < 5);
        check(x == 5);
    }

    void skip() {
        ;;;;
        check(true);
    }

    void nativeAssert() {
        // Do not use assert true since it is removed by javac
        int x = 1;
        assert (x>0);
    }


    @Pure
    boolean P3(int x, int y, int z) {
        return x > (y+z);
    }

    @Pure
    boolean P2(int x, int y) {
//          ^ warning: instance method 'P2' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        return P3(x,y,0);
    }

    @Pure
    boolean P(int x) {
//          ^ warning: instance method 'P' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        return P2(x,10);
    }
    
    void underscoreVariableName() {
        var _ = 3;
    }
    
    int methodWithResult() {
        return 3;
    }
    void ignoreCallResult() {
//       ^ warning: instance method 'ignoreCallResult' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        methodWithResult();
    }
}
