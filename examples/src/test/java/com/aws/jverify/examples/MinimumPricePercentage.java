package com.aws.jverify.examples;

import com.aws.jverify.Proof;
import com.aws.jverify.Pure;

import java.math.BigDecimal;

import static com.aws.jverify.JVerify.*;

public class MinimumPricePercentage {
    
    @Pure
    public BigDecimal computeDiscountedPrice(BigDecimal startingPrice, 
                                   BigDecimal flatDiscount, 
                                   BigDecimal discountFactor) 
    {
        // Percentage discount should be less than 50%
        precondition(discountFactor.compareTo(BigDecimal.valueOf(5, 1)) < 0);
        
        // Flat discount should be less than 20%
        precondition(flatDiscount.compareTo(startingPrice.multiply(BigDecimal.valueOf(3, 1))) < 0);
        // We've accidentally written 3 instead of 2 
        
        return startingPrice.
                multiply(BigDecimal.ONE.subtract(discountFactor)).
                subtract(flatDiscount);
    }

    // Discounted price should be at least 30% of the original
    final static BigDecimal minimumRemainingPrice = BigDecimal.valueOf(3, 1);
    
    @Proof
    void discountKeepsThirtyPercentAtLeast(BigDecimal startingPrice,
                                           BigDecimal flatDiscount,
                                           BigDecimal discountFactor) {
        postcondition(() -> {
//      ^^^^^^^^^^^^^ Related: this is the postcondition that could not be proven
            var result = computeDiscountedPrice(startingPrice, flatDiscount, discountFactor);
            return result.compareTo(startingPrice.multiply(minimumRemainingPrice)) >= 0;
        });
        
        preconditionOf(computeDiscountedPrice(startingPrice, flatDiscount, discountFactor));
    
        return;
//      ^^^^^^ Error: could not prove postcondition on this return path
    }
}
