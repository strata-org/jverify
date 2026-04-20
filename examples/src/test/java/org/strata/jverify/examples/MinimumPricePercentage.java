package org.strata.jverify.examples;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;

import java.math.BigDecimal;

import static org.strata.jverify.JVerify.*;

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
    
    @Erased
    BigDecimal discountKeepsThirtyPercentAtLeast(BigDecimal startingPrice,
                                           BigDecimal flatDiscount,
                                           BigDecimal discountFactor) {
        postcondition((BigDecimal result) -> {
//      ^^^^^^^^^^^^^ Related: this is the postcondition that could not be proven
            return result.compareTo(startingPrice.multiply(minimumRemainingPrice)) >= 0;
        });

        copyPrecondition(computeDiscountedPrice(startingPrice, flatDiscount, discountFactor));
        
        return computeDiscountedPrice(startingPrice, flatDiscount, discountFactor);
//      ^^^^^^ Error: could not prove postcondition on this return path
    }
}
