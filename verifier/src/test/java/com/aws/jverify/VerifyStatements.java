package com.aws.jverify;

import java.util.function.Predicate;
import java.util.logging.XMLFormatter;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.decreases;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody"})
class VerifyStatements {
    void breakk() {
        var x = 0;
        while(true) {
            decreases(10 - x);
            if (P(x)) {
                break;
            }
            x = x + 1;
        }
    }
    
    void continuee() {
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
        int i;
        int x = 0;
        for(i = 0; i < 5; i = i + 1) {
            x += 1;
        }
        check(i == 5);
    }
    
//    void labelledBreak() {
//        outerLoop:
//        for (int i = 0; i < 5; i++) {
//            for (int j = 0; j < 5; j++) {
//                if (i == 2 && j == 2) {
//                    System.out.println("  Breaking out of outer loop");
//                    break outerLoop; // Breaks out of the loop labeled 'outerLoop'
//                }
//            }
//        }
//        System.out.println("After loops");
//    }
    
    @Pure
    boolean P(int x) {
        return x > 10;
    }
}
