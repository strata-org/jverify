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
//          ^^^^^^^^ Related: this is the invariant that could not be proven
        return numbers.stream().reduce(0, Integer::sum) == sum;
    }
    
    void addNumber(int value) {
        modifies(numbers);
        var x = numbers.add(value);
        // we forgot to update sum
        return;
//      ^^^^^^ Error: could not prove invariant on return    
    }
}
