package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
// Loop invariants on these (formerly instance, now-translated) methods fail to
// verify: the invariant-not-established/maintained diagnostic is a Strata-side
// LoopElim limitation (the failure points at the loop, not the invariant) that
// is independent of static-vs-instance — confirmed by making a method static and
// seeing the same failures. Re-enable when the Strata loop-invariant handling lands.
@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 13, errorCount = 0)
class VerifyStatements {
    void forLoop() {
        int i = 0;
        for(i = 0; i < 5; i = i + 1) {
        }
        check(i == 5);
    }

    void nestedForLoop() {
        int x = 0;
        for (int i = 0; i < 5; i = i + 1) {
            invariant(x == i * 5);
            for (int j = 0; j < 5; j = j + 1) {
                invariant(x == j + i * 5);
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
            
            for (int j = 0; j < 5; j = j + 1) {
                invariant(i <= 2 ? x == j + i * 5 : x == j + 12 + (i - 3) * 5);
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
            invariant(x <= 5);
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
        return P3(x,y,0);
    }

    @Pure
    boolean P(int x) {
        return P2(x,10);
    }
    
    void underscoreVariableName() {
        var _ = 3;
    }
    
    int methodWithResult() {
        return 3;
    }
    void ignoreCallResult() {
        methodWithResult();
    }
}
