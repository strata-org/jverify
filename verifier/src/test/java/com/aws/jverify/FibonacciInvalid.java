package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class FibonacciInvalid {
    @Pure
    @Erased
    static @Unbounded @Nat int Spec(@Unbounded @Nat int n) {
        return n < 2 ? n : Spec(n - 1) + Spec(n - 2);
    }

    public static @Nat int Implementation(@Nat int n)
    {
        postcondition((Integer r) -> r == Spec(n));

        if (n == 0) {
            return 1; // postcondition failure
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
            int addition = result + previousResult; // overflow failure
            previousResult = result;
            result = addition;
        }
        return result;
    }

    @Proof
    static void SpecIsIncreasing(@Unbounded @Nat int i, @Unbounded @Nat int j)
    {
        precondition(i <= j);
        postcondition(Spec(i) <= Spec(j));

        @Nat int x = i; // overflow failure
        while(x < j)
        {
            invariant(x <= j);
            invariant(Spec(i) <= Spec(x));
            x = x + 1; // overflow failure
        }
    }
}
