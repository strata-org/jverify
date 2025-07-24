package com.aws.jverify.examples;

import com.aws.jverify.CheckPreconditionsAtRuntime;

import java.math.BigDecimal;

import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

public class PositivePrice {
    // Discounted price should be at least 30% of the original
    final static BigDecimal minimumRemainingPrice = BigDecimal.valueOf(3, 1);
    
    public BigDecimal computePrice(BigDecimal startingPrice, 
                                   BigDecimal flatDiscount, 
                                   BigDecimal discountFactor) 
    {
        // Percentage discount should be less than 50%
        precondition(discountFactor.compareTo(BigDecimal.valueOf(5, 1)) < 0);
        
        // Flat discount should be less than 20%
        precondition(flatDiscount.compareTo(startingPrice.multiply(BigDecimal.valueOf(3, 1))) < 0);
        // We've accidentally written 3 instead of 2 
        
        postcondition((BigDecimal result) -> 
                result.compareTo(startingPrice.multiply(minimumRemainingPrice)) >= 0);
//      ^^^^^^^^^^^^^ Related: this is the postcondition that could not be proven
        
        return startingPrice.
                multiply(BigDecimal.ONE.subtract(discountFactor)).
                subtract(flatDiscount);
//      ^^^^^^ Error: could not prove postcondition on this return path
    }
}
