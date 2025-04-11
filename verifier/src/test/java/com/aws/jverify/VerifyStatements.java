package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
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
    
    void skip() {
        ;;;;
        check(true);
    }

    void nativeAssert() {
        assert true;
    }
    
    @Pure
    boolean P(int x) {
        return x > 10;
    }
}
