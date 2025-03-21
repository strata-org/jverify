package com.aws.verifier.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

import java.util.List;

import net.jqwik.api.*;

@ExternalContract(List.class)
public class ListContract<E> {
    Sequence<E> values;

    boolean add(E e) {
        postcondition(values.equals(old(values).concat(Sequence.single(e))));
        postcondition((Boolean r) -> r);
        throw new ContractException();
    }

    E get(int index) {
        precondition(index < values.size());
        postcondition((Integer e) -> e == values.get(index));
        throw new ContractException();
    }
    
    @Proof
    @AsProperty
    void addAllLengthAddition(@ForAll List<E> first, @ForAll List<E> second) {
        postcondition(() -> {
            first.addAll(second);
            return first.size() == old(first.size()) + second.size();
        });
    }
    
    @Property
    boolean addAllLengthAddition_property(@ForAll List<E> first, @ForAll List<E> second) {
        var old1 = first.size();
        first.addAll(second);
        return first.size() == old1 + second.size();
    }
}
