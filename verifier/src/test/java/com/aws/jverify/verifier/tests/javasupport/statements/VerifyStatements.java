package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
@JVerifyTest(methodsVerified = 19, errorCount = 0)
class VerifyStatements {
    void whileWithBreak() {
        var x = 0;
        while(true) {
            decreases(10 - x);
            if (P(x)) {
                break;
            }
            x = x + 1;
        }
    }
    
    void whileWithContinue() {
        var x = 0;
        while(x < 10) {
            if (P(x)) {
                continue;
            }
            check(!P(x));
            x = x + 1;
        }
    }
    
    void forLoop() {
        int i = 0;
        for(i = 0; i < 5; i = i + 1) {
        }
        check(i == 5);
    }

    void forLoopBreak() {
        int i = 0;
        int x = 0;
        for(i = 0; true; i = i + 1) {
            decreases(5 - i);
            invariant(i <= 5);
            invariant(x == i);
            if (i == 5) {
                break;
            }
            x = x + 1;
        }
        check(i == 5);
    }

    void forLoopContinue() {
        int i = 0;
        int x = 0;
        for(i = 0; i < 5; i = i + 1) {
            invariant(i <= 3 ? x == i : x == i - 1);
            if (i == 3) {
                continue;
            }
            x = x + 1;
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

    void nestedForLoopBreak() {
        int x = 0;
        outerLoop:
        for (int i = 0; i < 5; i = i + 1) {
            invariant(x == i * 5);
            invariant(i <= 2);

            for (int j = 0; j < 5; j = j + 1) {
                invariant(x == j + i * 5);
                invariant(i == 2 ? j <= 2 : true);
                if (i == 2 && j == 2) {
                    check(x == 12);
                    break outerLoop;
                }
                x = x + 1;
            }
        }
        check(x == 12);
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

    void twoForLoops() {
        int cnt = 0;
        for (var i = 0; i < 10; i++) {
            invariant(cnt==i);
            cnt++;
        }
        check(cnt==10);
        for (var i = 0; i < 10; i++) {
            invariant(cnt-10==i);
            cnt++;
        }
        check(cnt==20);
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
