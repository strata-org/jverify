package com.aws.jverify.examples;

import com.aws.jverify.*;
import java.util.ArrayList;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static com.aws.jverify.JVerify.*;

public class SumCache {
    ArrayList<Integer> numbers = new ArrayList<>();
    int sum = 0;
    final static BinaryOperator<Integer> integerSum = SumCache::unsafeSum;

    @Pure
    @Invariant
    boolean validSum() {
        reads(this);
        reads(numbers);
        return this != (Object)numbers && numbers.stream().reduce(0, integerSum) == sum;
//          ^^^^^^^^ Related: this is the invariant that could not be proven
    }
    
    public void addNumber(int value) {
        precondition(!willAddOverflow(sum, value));
        modifies(this);
        modifies(numbers);

        sum = integerSum.apply(sum, value); // TODO allow introspecting integerSum so we can replace this with 'sum = sum + value'
        var x = numbers.add(value);
        return;
//      ^^^^^^ Error: could not prove invariant on return    
    }
    
    @Pure
    public static boolean willAddOverflow(int a, int b) {
        if (a > 0 && b > 0) {
            return a > Integer.MAX_VALUE - b;
        }
        if (a < 0 && b < 0) {
            return a < Integer.MIN_VALUE - b;
        }
        return false; // Different signs won't overflow
    }

    @Verify(false)
    @Pure 
    static int unsafeSum(int a, int b) {
        return a + b;
    }
}
