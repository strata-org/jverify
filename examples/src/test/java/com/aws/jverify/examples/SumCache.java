package com.aws.jverify.examples;

import com.aws.jverify.Invariant;
import com.aws.jverify.Pure;
import java.util.ArrayList;

import static com.aws.jverify.JVerify.*;

public class SumCache {
    ArrayList<Integer> numbers = new ArrayList<>();
    int sum = 0;

    @Pure
    @Invariant
    boolean validSum() {
        reads(this);
        reads(numbers);
        return numbers.stream().reduce(0, Integer::sum) == sum;
//          ^^^^^^^^ Related: this is the invariant that could not be proven
    }
    
    public void addNumber(int value) {
        precondition(!willAddOverflow(sum, value));
        modifies(this);
        modifies(numbers);

        int before = numbers.stream().reduce(0, Integer::sum);
        int before2 = numbers.stream().reduce(0, Integer::sum);
        check(before == before2);

        check(before == sum);
        sum = sum + value;
        var preAddSum = sum;
        var x = numbers.add(value);
        check(sum == preAddSum);
        var after = numbers.stream().reduce(0, Integer::sum);
        check(after == before + value);
        check(after == sum);
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
}
