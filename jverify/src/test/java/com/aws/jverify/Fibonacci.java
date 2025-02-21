package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class Fibonacci {
    @Pure
    @Ghost
    static @Unbounded @Nat int Spec(@Unbounded @Nat int n) {
        return n < 2 ? n : Spec(n - 1) + Spec(n - 2);
    }

    public static @Nat int Implementation(@Nat int n)
    {
        requires(Spec(n) <= 0x7fffffff /*Integer.MAX_VALUE*/);
        ensures((Integer r) -> r == Spec(n));        

        if (n == 0) {
            return 1;
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
            SpecIsIncreasing(i, n);
            int addition = result + previousResult;
            previousResult = result;
            result = addition;
        }
        return result;
    }
    
    @Proof
    static void SpecIsIncreasing(@Unbounded @Nat int i, @Unbounded @Nat int j)
    {
        requires(i <= j);
        ensures(Spec(i) <= Spec(j));

        @Unbounded @Nat int x = i;
        while(x < j)
        {
            invariant(x <= j);
            invariant(Spec(i) <= Spec(x));
            x = x + 1;
        }
    }
}