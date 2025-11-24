package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.*;
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
        var r = IntStream.range(0, 10).allMatch(value -> {
            precondition(cast(IntStream.range(0, 10), IntStreamContract.class).values().contains(value));
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
        // Using a method instead of a final field, since Dafny does not support abstract constants in traits
        @Erased
        @Pure
        public JVerify.IntSequence values() {
            throw new ContractException();
        }

        @Pure
        public boolean allMatch(IntPredicate predicate) {
            precondition(JVerify.forall((int i) ->
                    implies(values().contains(i),
                            preconditionOf(predicate.test(i)))
            ));
            throw new ContractException();
        }
        
        @Pure
        public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
            precondition(JVerify.forall((int i) ->
                    implies(values().contains(i),
                            preconditionOf(mapper.apply(i)))
            ));
            throw new ContractException();
        }

        @Pure
        public static IntStream range(int startInclusive, int endExclusive) {
            precondition(startInclusive <= endExclusive);
            postcondition((IntStreamContract r) -> jequals(r.values(), JVerify.range(startInclusive, endExclusive)));
            throw new ContractException();
        }
    }

}
