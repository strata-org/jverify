package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

class Fibonacci {
    @Pure
    @Erased
    static @Unbounded @Nat int Spec(@Unbounded @Nat int n) {
        return n < 2 ? n : Spec(n - 1) + Spec(n - 2);
    }

    public static @Nat int Implementation(@Nat int n)
    {
        postcondition((int r) -> r == Spec(n));
        
        // this precondition prevents overflow later in this method
        precondition(Spec(n) <= Integer.MAX_VALUE);

        if (n == 0) {
            return 0;
        }

        int previousResult = 0;
        int result = 1;
        int i = 1;
        while(i < n)
        {
            invariant(result == Spec(i));
            invariant(previousResult == Spec(i - 1));
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
        postcondition(Spec(i) <= Spec(j));

        @Unbounded @Nat int x = i;
        while(x < j)
        {
            invariant(x <= j);
            invariant(Spec(i) <= Spec(x));
            x = x + 1;
        }
    }
}
