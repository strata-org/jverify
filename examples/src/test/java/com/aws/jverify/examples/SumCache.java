package com.aws.jverify.examples;

import com.aws.jverify.Invariant;
import com.aws.jverify.Pure;

import java.util.ArrayList;

import static com.aws.jverify.JVerify.*;

public class SumCache {
    ArrayList<Integer> numbers = new ArrayList<>();
    int sum;

    @Invariant
    boolean validSum() {
        reads(this);
        reads(numbers);
//          ^^^^^^^^ Related: this is the invariant that could not be proven
        return numbers.stream().reduce(0, Integer::sum) == sum;
    }

    void addNumber(int value) {
        precondition(!sumWillOverflow(sum, value));
        modifies(this);
        modifies(numbers);
        numbers.add(value);
        // sum = sum + value;
        return;
//      ^^^^^^ Error: could not prove invariant on return    
    }

    @Pure
    public static boolean sumWillOverflow(int a, int b) {
        if (a > 0 && b > 0) {
            return a > Integer.MAX_VALUE - b;
        }
        if (a < 0 && b < 0) {
            return a < Integer.MIN_VALUE - b;
        }
        return false; // Different signs won't overflow
    }
}