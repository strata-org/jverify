package org.strata.jverify.langref.jls14.s14_20;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.1 -- try-catch: a try block guards statements; when an exception is
// thrown, control transfers to the first catch clause whose parameter type
// matches. Exercises matching catch, multi-catch, and order of catch clauses.
class TryCatch_2 {
    static int classify(int v) {
        try {
            if (v == 0) {
                throw new IllegalArgumentException("zero");
            }
            if (v < 0) {
                throw new IllegalStateException("neg");
            }
            return 100; // positive: try completes normally
        } catch (IllegalArgumentException e) {
            return 1;
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    static void run() {

        // matching first catch

        // matching second catch
        check(classify(-5) == 2);
    }
}
