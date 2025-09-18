package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

class Fibonacci {
    @Pure
    @Erased
    static @Unbounded @Nat int spec(@Unbounded @Nat int n) {
        return n < 2 ? n : spec(n - 1) + spec(n - 2);
    }

    public static @Nat int implementation(@Nat int n)
    {
        postcondition((int result) -> result == spec(n));
        
        // this precondition prevents overflow later in this method
        precondition(spec(n) <= Integer.MAX_VALUE);

        if (n == 0) {
            return 0;
        }

        int previousResult = 0;
        int result = 1;
        int i = 1;
        while(i < n)
        {
            invariant(result == spec(i));
            invariant(previousResult == spec(i - 1));
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
        postcondition(spec(i) <= spec(j));

        @Unbounded @Nat int x = i;
        while(x < j)
        {
            invariant(x <= j);
            invariant(spec(i) <= spec(x));
            x = x + 1;
        }
    }
}
