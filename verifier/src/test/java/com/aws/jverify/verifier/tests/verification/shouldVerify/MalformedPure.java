package com.aws.jverify.verifier.tests.verification.shouldVerify;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Optional;

public class MalformedPure {
    public static int getAverageInt(Collection<Integer> numbers) {
        var total = numbers.stream().reduce(0, Integer::sum);
        return total / numbers.size();
//                   ^ Error: possible division by zero
    }


    public static BigInteger getAverage2(Collection<BigInteger> numbers) {
        var total = numbers.stream().reduce(BigInteger.ZERO, BigInteger::add);
        return total.divide(BigInteger.valueOf(numbers.size()));
//             ^ Error: possible division by zero
    }

    public static Optional<BigInteger> getAverage(Collection<BigInteger> numbers) {
        if (numbers.size() == 0) {
            return Optional.empty();
        }
        var total = numbers.stream().reduce(BigInteger.ZERO, BigInteger::add);
        return Optional.of(total.divide(BigInteger.valueOf(numbers.size())));
    }
}
