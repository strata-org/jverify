package org.example;

import static org.strata.jverify.JVerify.*;

public class App {
    /** Returns the larger of {@code a} and {@code b}. */
    static int max(int a, int b) {
        postcondition((int res) -> res >= a && res >= b && (res == a || res == b));
        if (a >= b) {
            return a;
        }
        return b;
    }
}
