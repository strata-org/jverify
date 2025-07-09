package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Union;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 9, dafnyErrors = 0, useBuiltinContracts = true)
class Unions {
    static void testEither() {
        var intPredicate = new EvenIntPredicate();
        var strPredicate = new EvenStrPredicate();
        var left = new Left<Integer, String>(5);
        var right = new Right<Integer, String>("foobar");

        var mapLeft = left.bifoldMap(intPredicate, strPredicate);
        check(!mapLeft);

        var mapRight = right.bifoldMap(intPredicate, strPredicate);
        check(mapRight);
    }

    static class EvenIntPredicate implements Predicate<Integer> {
        @Override
        @Pure
        public boolean apply(Integer i) {
            return i % 2 == 0;
        }
    }

    static class EvenStrPredicate implements Predicate<String> {
        @Override
        @Pure
        public boolean apply(String s) {
            return s.length() % 2 == 0;
        }
    }
}

@SuppressWarnings("unused")
@Union
sealed interface Either<T, U> {
    @Pure
    boolean bifoldMap(Predicate<T> applyLeft, Predicate<U> applyRight);
}

@SuppressWarnings("unused")
@Contract(Either.class)
final class EitherContract<T, U> implements Either<T, U> {
    @Pure
    public boolean bifoldMap(Predicate<T> applyLeft, Predicate<U> applyRight) {
        throw new ContractException();
    }
}

record Left<T, U>(T value) implements Either<T, U> {
    @Override
    @Pure
    public boolean bifoldMap(Predicate<T> applyLeft, Predicate<U> applyRight) {
        postcondition((boolean b) -> b == applyLeft.apply(this.value()));
        return applyLeft.apply(value);
    }
}

record Right<T, U>(U value) implements Either<T, U> {
    @Override
    @Pure
    public boolean bifoldMap(Predicate<T> applyLeft, Predicate<U> applyRight) {
        postcondition((boolean b) -> b == applyRight.apply(this.value()));
        return applyRight.apply(value);
    }
}

interface Predicate<T> {
    @Pure
    boolean apply(T t);

    @SuppressWarnings("unused")
    @Contract(Predicate.class)
    class PredicateContract<T> implements Predicate<T> {
        @Override
        @Pure
        public boolean apply(T t) {
            throw new ContractException();
        }
    }
}