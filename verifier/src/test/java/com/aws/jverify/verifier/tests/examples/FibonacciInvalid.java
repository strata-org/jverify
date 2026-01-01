package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.Erased;
import com.aws.jverify.Nat;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 2, failedAssertions = 4)
class FibonacciInvalid {
    @Pure
    @Erased
    static @Unbounded @Nat int Spec(@Unbounded @Nat int n) {
        return n < 2 ? n : Spec(n - 1) + Spec(n - 2);
    }

    public static @Nat int Implementation(@Nat int n)
    {
        postcondition((int r) -> r == Spec(n));
//                               ^^^^^^^^^^^^ Related location: this is the postcondition that could not be proved

        if (n == 0) {
            return 1;
//          ^^^^^^^^^ Error: a postcondition could not be proved on this return path
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
//                         ^^^^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
            previousResult = result;
            result = addition;
        }
        return result;
    }

    @Erased
    static void SpecIsIncreasing(@Unbounded @Nat int i, @Unbounded @Nat int j)
    {
        precondition(i <= j);
        postcondition(Spec(i) <= Spec(j));

        @Nat int x = i;
//                   ^ Error: value does not satisfy the subset constraints of 'nat31'
        while(x < j)
        {
            invariant(x <= j);
            invariant(Spec(i) <= Spec(x));
            x = x + 1;
//              ^^^^^ Error: value does not satisfy the subset constraints of 'nat31'
        }
    }
}
