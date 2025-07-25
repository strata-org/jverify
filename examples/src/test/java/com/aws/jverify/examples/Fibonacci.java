package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

class Fibonacci {
    @Pure
    @Erased
    static @Unbounded @Nat int SlowButConcise(@Unbounded @Nat int n) {
        return n < 2 ? n : SlowButConcise(n - 1) + SlowButConcise(n - 2);
    }

    public static @Nat int FastButVerbose(@Nat int n)
    {
        postcondition((int result) -> result == SlowButConcise(n));
        
        // this precondition prevents overflow later in this method
        precondition(SlowButConcise(n) <= Integer.MAX_VALUE);

        if (n == 0) {
            return 0;
        }

        int previousResult = 0;
        int result = 1;
        int i = 1;
        while(i < n)
        {
            invariant(result == SlowButConcise(i));
            invariant(previousResult == SlowButConcise(i - 1));
            invariant(i <= n);

            i = i + 1;

            // Using the precondition that the final result fits in an int32
            // And the knowledge that the intermediate results are smaller than the result
            // We can show the addition always fits in an int32
            SpecIsIncreasing(i, n);
            int addition = result + previousResult;
            previousResult = result;
            result = addition;
        }
        return result;
    }

    /**
     * Proves that Fibonacci numbers increase in value with increading input value
     */
    @Erased
    static void SpecIsIncreasing(@Unbounded @Nat int i, @Unbounded @Nat int j)
    {
        precondition(i <= j);
        postcondition(SlowButConcise(i) <= SlowButConcise(j));

        @Unbounded @Nat int x = i;
        while(x < j)
        {
            invariant(x <= j);
            invariant(SlowButConcise(i) <= SlowButConcise(x));
            x = x + 1;
        }
    }
}
