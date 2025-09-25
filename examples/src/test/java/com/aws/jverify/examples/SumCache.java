package com.aws.jverify.examples;

import com.aws.jverify.Invariant;
import com.aws.jverify.Pure;
import java.util.ArrayList;
import java.util.function.BinaryOperator;

import static com.aws.jverify.JVerify.*;

public class SumCache {
    ArrayList<Integer> numbers = new ArrayList<>();
    int sum = 0;
    final static BinaryOperator<Integer> integerSum = Integer::sum;

    @Pure
    @Invariant
    boolean validSum() {
        reads(this);
        reads(numbers);
        return numbers.stream().reduce(0, integerSum) == sum;
//          ^^^^^^^^ Related: this is the invariant that could not be proven
    }
    
    public void addNumber(int value) {
        precondition(!willAddOverflow(sum, value));
        modifies(this);
        modifies(numbers);

        BinaryOperator<Integer> a1 = integerSum;
        BinaryOperator<Integer> a2 = integerSum;
        check(a1 == a2);
        int before = numbers.stream().reduce(0, a1);
        int before2 = numbers.stream().reduce(0, a2);
        check(before == before2); 
        // fails because the two lambdas are different
        // we could try to let the two method references re-use the same lambda,
        // but we want this to work even if we had used two separate but equivalent lambdas
        // do we need 

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
