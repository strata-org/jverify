package com.aws.jverify.examples;

import com.aws.jverify.*;
import java.util.ArrayList;

import static com.aws.jverify.JVerify.*;

public class SumCache {
    ArrayList<Integer> numbers = new ArrayList<>();
    int sum = 0;

    @Pure
    @Invariant
    boolean validSum() {
//          ^^^^^^^^ Related location: this is the postcondition that could not be proved
        reads(this); reads(numbers);
        return this != (Object)numbers && 
            numbers.stream().reduce(0, SumCache::unsafeSum) == sum;
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Related location: this proposition could not be proved
    }
    
    public void addNumber(int value) {
        precondition(!willAddOverflow(sum, value));
        modifies(this); modifies(numbers);

        sum = sum + value;
        // numbers.add(value);
        return;
//      ^^^^^^^ Error: a postcondition could not be proved on this return path    
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

    @Pure 
    static int unsafeSum(int a, int b) {
        assume(false); // TODO enable skipping the checks but keeping the functional body using @Verify(false)
        return a + b;
    }
}
