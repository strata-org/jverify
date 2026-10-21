package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 0)
class CsvFields {
    static final int COMMA = ',';
    static final int QUOTE = '"';

    @Pure
    @Erased
    static boolean quotedAfter(int[] in, @Unbounded int hi) {
        reads(in);
        decreases(hi);
        if (hi <= 0) {
            return false;
        }
        return quotedAfter(in, hi - 1) != (in[hi - 1] == QUOTE);
    }

    @Pure
    @Erased
    static @Unbounded int separatorsBelow(int[] in, @Unbounded int hi) {
        reads(in);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return separatorsBelow(in, hi - 1) + (in[hi - 1] == COMMA && !quotedAfter(in, hi - 1) ? 1 : 0);
    }

    static int countFields(int[] in, int[] ends) {
        precondition(in.length <= 1000000);
        precondition(ends.length >= in.length + 1);
        modifies(ends);
        postcondition((int res) -> res == -1 || res == separatorsBelow(in, in.length) + 1);
        postcondition((int res) -> implies(res == -1, quotedAfter(in, in.length)));

        int fields = 0;
        boolean quoted = false;
        int i = 0;
        while (i < in.length) {
            modifies(ends);
            invariant(0 <= i && i <= in.length);
            invariant(quoted == quotedAfter(in, i));
            invariant(fields == separatorsBelow(in, i));

            int c = in[i];
            if (c == QUOTE) {
                if (quoted && i + 1 < in.length && in[i + 1] == QUOTE) {
                    i = i + 2;
                    continue;
                }
                quoted = !quoted;
            } else if (c == COMMA && !quoted) {
                ends[fields] = i;
                fields = fields + 1;
            }
            i = i + 1;
        }
        if (quoted) {
            return -1;
        }
        ends[fields] = in.length;
        return fields + 1;
    }
}
