package org.strata.jverify.builtin;

import org.strata.jverify.Pure;
import org.strata.jverify.JVerify.Sequence;
import java.util.function.BinaryOperator;

class SequenceHelper {
    @Pure
    public static <T> T reduce(Sequence<T> sequence, T identity, BinaryOperator<T> accumulator) {
        if (sequence.size() == 0) {
            return identity;
        }
        return accumulator.apply(reduce(sequence.subsequence(0, sequence.size() - 1), identity, accumulator), sequence.get(sequence.size() - 1));
    }
}