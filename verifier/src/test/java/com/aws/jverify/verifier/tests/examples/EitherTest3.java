package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;
import org.jheaps.annotations.VisibleForTesting;

import java.util.function.Function;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 2, dafnyErrors = 0)
class EitherTest3 {
    record Q(int i) {}
    record R(int i) {}
    record S(int i) {}
    
    S nestedEithers(Either<Either<Q, R>, S> either) {
        return either.fold(
            qr -> {
                decreases(1);
                return qr.fold(
                        q -> {
                            decreases(0);
                            return new S(q.i + 1);
                        },
                        r -> {
                            decreases(0);
                            return new S(r.i + 2);
                        });
            }, 
            s -> {
                decreases(0);
                return new S(s.i + 3);
            });
    }
    
    public sealed interface Either<T, U> {
        @Pure
        public <V> V fold(Function<T, V> useLeft, Function<U, V> useRight);

        @Contract(Either.class)
        class EitherContract<T,U> {
            @Pure
            public <V> V fold(Function<T, V> useLeft, Function<U, V> useRight) {
                throw new ContractException();
            }
        }
        
        public record Left<T, U>(T value) implements Either<T, U> {

            @Pure
            @Override
            public <V> V fold(Function<T, V> useLeft, Function<U, V> useRight) {
                decreases(decreasesOf(useLeft.apply(value)));
                return useLeft.apply(value);
            }
        }

        public record Right<T, U>(U value) implements Either<T, U> {
            @Pure
            @Override
            public <V> V fold(Function<T, V> useLeft, Function<U, V> useRight) {
                decreases(decreasesOf(useRight.apply(value)));
                return useRight.apply(value);
            }
        }
    }

    @Contract(value = Function.class, pure = true)
    abstract static class FunctionContract<T, R> implements Function<T, R> {
        @Pure
        public R apply(T t) {
            decreases(isAbstract());
            throw new ContractException();
        }
    }
}
