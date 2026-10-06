package org.strata.jverify.langref.jls14.s14_30;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.1 -- Kinds of Patterns: a record pattern deconstructs a record value into its components.
class RecordPattern {
    record Point(int x, int y) {}

    static void run() {
        Object o = new Point(3, 4);
        int sum = 0;
        // Record pattern `Point(int a, int b)` matches and binds the record components.
        if (o instanceof Point(int a, int b)) {
            sum = a + b;
        }
        check(sum == 7);
    }
}
