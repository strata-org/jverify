package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 4)
class PrintTokensInvalid {
    static final int SPACE = 0;
    static final int IDENTIFIER = 1;
    static final int NUMBER = 2;
    static final int SPECIAL = 3;

    @Transparent
    static boolean isLetter(int c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || c == '_';
    }

    @Transparent
    static boolean isDigit(int c) {
        return '0' <= c && c <= '9';
    }

    @Transparent
    @Erased
    static boolean spansCover(int[] start, int[] end, int count, int upTo) {
        reads(start);
        reads(end);
        return forall((int t) -> implies(0 <= t && t < count, start[t] < end[t] && end[t] <= upTo))
                && forall((int t) -> implies(0 < t && t < count, end[t - 1] <= start[t]));
    }

    @Transparent
    @Erased
    static boolean kindsMatch(int[] in, int[] start, int[] end, int[] kind, int count) {
        reads(in);
        reads(start);
        reads(end);
        reads(kind);
        return forall((int t, int k) -> implies(0 <= t && t < count && start[t] <= k && k < end[t],
                (kind[t] == IDENTIFIER && (isLetter(in[k]) || (k > start[t] && isDigit(in[k]))))
                || (kind[t] == NUMBER && isDigit(in[k]))
                || (kind[t] == SPECIAL && end[t] == start[t] + 1)));
    }

    static int tokenize(int[] in, int[] start, int[] end, int[] kind) {
        precondition(in.length <= 1000000);
        precondition(start.length >= in.length && end.length >= in.length && kind.length >= in.length);
        precondition(in != start && in != end && in != kind && start != end && start != kind && end != kind);
        modifies(start);
        modifies(end);
        modifies(kind);
        postcondition((int count) -> 0 <= count && count <= in.length);
        postcondition((int count) -> spansCover(start, end, count, in.length));
        postcondition((int count) -> kindsMatch(in, start, end, kind, count));

        int count = 0;
        int i = 0;
        while (i < in.length) {
            modifies(start);
            modifies(end);
            modifies(kind);
            invariant(0 <= i && i <= in.length && 0 <= count && count <= i);
            invariant(spansCover(start, end, count, i));
            invariant(kindsMatch(in, start, end, kind, count));

            int c = in[i];
            if (c == ' ' || c == '\n' || c == '\t') {
                i = i + 1;
                continue;
            }
            int j = i + 1;
            int k;
            if (isLetter(c)) {
                k = IDENTIFIER;
                while (isLetter(in[j]) || isDigit(in[j])) {
//                              ^^^^^ Error: precondition could not be proved
                    invariant(i < j && j <= in.length);
                    j = j + 1;
                }
            } else if (isDigit(c)) {
                k = NUMBER;
                while (j < in.length && isDigit(in[j])) {
                    invariant(i < j && j <= in.length);
                    j = j + 1;
                }
            } else {
                k = SPECIAL;
            }
            start[count] = i;
            end[count] = j;
            kind[count] = k;
            count = count + 1;
            i = j;
        }
        return count;
    }
}
