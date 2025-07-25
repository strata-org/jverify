package com.aws.jverify.examples;

import com.aws.jverify.Invariant;

import java.util.List;

public class SumCache {
    List<Integer> numbers;
    int sum;
    
    @Invariant
    boolean validSum() {
//          ^^^^^^^^ Related: this is the invariant that could not be proven
        return numbers.stream().reduce(0, Integer::sum) == sum;
    }
    
    void addNumber(int value) {
        numbers.add(value);
        // we forgot to update sum
        return;
//      ^^^^^^ Error: could not prove invariant on return    
    }
}
