package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 5, dafnyErrors = 0)
public class AbstractContracts {

    @SuppressWarnings("ConstantValue")
    void foo() {
        var temp = IntStream.range(0, 10);
        var r = temp.allMatch(value -> {
            precondition(cast(temp, IntStreamContract.class).values.contains(value));
            return value == 2;
        });
    }
}

// TODO make inner classes.
@Contract(IntPredicate.class)
class IntPredicateContract implements IntPredicate {
    @Pure
    public boolean test(int value) {
        precondition(isAbstract(this));
        throw new ContractException();
    }
}

@Contract(IntStream.class)
abstract class IntStreamContract implements IntStream {
    public final JVerify.IntSequence values = null;

    @Pure
    public boolean allMatch(IntPredicate predicate) {
        precondition(JVerify.forall((int i) ->
                implies(values.contains(i),
                        preconditionOf(predicate.test(i)))
        ));
        throw new ContractException();
    }

    @Pure
    public static IntStream range(int startInclusive, int endExclusive) {
        postcondition((IntStreamContract r) -> jequals(r.values, JVerify.range(startInclusive, endExclusive)));
        throw new ContractException();
    }
}
