package com.aws.jverify.verifier.tests.verification.shouldVerify;

import java.util.Collection;

public class MalformedPure {
    public static int getAverage(Collection<Integer> numbers) {
        var total = numbers.stream().reduce(0, Integer::sum);
        return total / numbers.size();
//                   ^ Error: possible division by zero
    }
}
