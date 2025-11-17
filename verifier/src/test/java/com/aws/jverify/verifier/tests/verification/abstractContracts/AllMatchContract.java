package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 9, dafnyErrors = 0)
public class AllMatchContract {

    @SuppressWarnings("ConstantValue")
    void foo() {
        var temp = IntStream.range(0, 10);
        var r = temp.allMatch(value -> {
            precondition(cast(temp, IntStreamContract.class).values.contains(value));
            check(0 <= value && value <= 10);
            return value == 2;
        });
        check(true);
    }

    @Contract(IntPredicate.class)
    static class IntPredicateContract implements IntPredicate {
        @Pure
        public boolean test(int value) {
            precondition(isAbstract());
            throw new ContractException();
        }
    }

    @Contract(IntStream.class)
    static abstract class IntStreamContract implements IntStream {
        // Temporarily assigning this fixed value here, since Dafny does not support abstract constants in traits
        // And JVerify does not yet support writing this as an abstract method
        public final JVerify.IntSequence values = JVerify.range(0, 10);

        @Pure
        public boolean allMatch(IntPredicate predicate) {
            precondition(JVerify.forall((int i) ->
                    implies(values.contains(i),
                            preconditionOf(predicate.test(i)))
            ));
            throw new ContractException();
        }
        
        @Pure
        public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
            precondition(JVerify.forall((int i) ->
                    implies(values.contains(i),
                            preconditionOf(mapper.apply(i)))
            ));
            throw new ContractException();
        }

        @Pure
        public static IntStream range(int startInclusive, int endExclusive) {
            precondition(startInclusive <= endExclusive);
            postcondition((IntStreamContract r) -> jequals(r.values, JVerify.range(startInclusive, endExclusive)));
            throw new ContractException();
        }
    }

}
