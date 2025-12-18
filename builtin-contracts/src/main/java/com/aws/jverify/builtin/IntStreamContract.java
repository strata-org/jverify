package com.aws.jverify.builtin;

import com.aws.jverify.*;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.aws.jverify.JVerify.*;

@Contract(IntStream.class)
abstract class IntStreamContract implements IntStream {
    @Erased
    @EmptyContract
    @Pure
    public abstract IntSequence values(); // final fields not well supported yet
    
    @Pure
    public boolean allMatch(IntPredicate predicate) {
        precondition(JVerify.forall((int i) ->
                implies(values().contains(i),
                        preconditionOf(predicate.test(i)))
        ));
        postcondition((boolean r) -> {
            return forall((int i) -> r == implies(0 <= i && i < values().size(), 
                     predicate.test(values().get(i))));
        });
        throw new ContractException();
    }

    @Pure
    public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
        precondition(JVerify.forall((int i) ->
                implies(values().contains(i),
                        preconditionOf(mapper.apply(i)))
        ));
        postcondition((Stream<U> r) -> {
            var returnedContract = StreamContract.fromStream(r);
            return returnedContract.elements.size() == values().size() &&
              forall((int i) -> implies(0 <= i && i < values().size(), returnedContract.elements.get(i) == mapper.apply(values().get(i))));      
        });
        throw new ContractException();
    }

    @Pure
    public static IntStream range(int startInclusive, int endExclusive) {
        precondition(startInclusive <= endExclusive);
        postcondition((IntStreamContract c) -> jequals(c.values(), JVerify.range(startInclusive, endExclusive)));
        throw new ContractException();
    }
}