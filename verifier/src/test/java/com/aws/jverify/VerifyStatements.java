package com.aws.jverify;

import java.util.function.Predicate;
import java.util.logging.XMLFormatter;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.decreases;

@SuppressWarnings("ConditionalBreakInInfiniteLoop")
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
    
    @Pure
    boolean P(int x) {
        return x > 10;
    }
}
