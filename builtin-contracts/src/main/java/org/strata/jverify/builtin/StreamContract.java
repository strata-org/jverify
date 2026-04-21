package org.strata.jverify.builtin;

import org.strata.jverify.*;
import java.util.function.Function;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import static org.strata.jverify.JVerify.*;

@Contract(value = Stream.class, pure = true)
abstract class StreamContract<T> implements Stream<T> {

    public JVerify.Sequence<T> elements;

    @Pure
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return SequenceHelper.reduce(elements, identity, accumulator);
    }

    @Pure
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        precondition(JVerify.forall((int i) ->
                implies(0 <= i && i < elements.size(),
                        preconditionOf(mapper.apply(elements.get(i))))
        ));
        postcondition((StreamContract<R> r) ->
                elements.size() == r.elements.size() &&
                        forall((int index) -> implies(0 <= index && index < elements.size(),
                                jequals(mapper.apply(elements.get(index)), r.elements.get(index)))));
        throw new ContractException();
    }
    
    @Erased
    @Pure
    public static <T> StreamContract<T> fromStream(Stream<T> stream) {
        return JVerify.<Stream<T>, StreamContract<T>>cast(stream);
    }
}