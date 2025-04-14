package com.aws.jverify;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.decreases;

class Termination {
    void Recursive(@Nat int x, @Nat int y) {
        decreases(y, x);
        
        if (x == 0) {
            if (y == 0) {
            } else {
                Recursive(y, y - 1);
            }
        }
        else {
            Recursive(x - 1, y);
        }
    }

    void WrongOrder(@Nat int x, @Nat int y) {
        decreases(x, y);

        if (x == 0) {
            if (y == 0) {
            } else {
                WrongOrder(y, y - 1);
//              ^^^^^^^^^^^^^^^^^^^^ Error: decreases clause might not decrease
            }
        }
        else {
            WrongOrder(x - 1, y);
        }
    }
}
