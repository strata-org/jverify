package com.aws.jverify.builtin;

import com.aws.jverify.*;
import java.util.List;
import static com.aws.jverify.JVerify.*;

@Contract(value = List.class, pure = true)
abstract class ListContract<E> implements List<E> {
    
    @Pure
    static <E> ImmutableList<E> of() {
        postcondition((List<E> r) -> r instanceof ImmutableList<E> il && 
                il.size() == 0);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1) {
        postcondition((List<E> r) -> r instanceof ImmutableList<E> il && 
                il.size() == 1 && 
                il.get(0) == e1);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1, E e2) {
        postcondition((List<E> r) -> r instanceof ImmutableList<E> il && 
                il.size() == 2 && 
                il.get(0) == e1 && 
                il.get(1) == e2);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1, E e2, E e3) {
        postcondition((List<E> r) -> r instanceof ImmutableList<E> il && 
                il.size() == 3 && 
                il.get(0) == e1 && il.get(1) == e2 && 
                il.get(2) == e3);
        throw new ContractException();
    }

    @Override
    @Pure
    public E get(@Nat int index) {
        reads(everything());
        decreases(size());
        precondition(0 <= index && index < size());
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        reads(everything());
        decreases(0);
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        reads(everything());
        decreases(0);
        postcondition((boolean r) -> r == (size() == 0));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        reads(everything());
        decreases(size());
        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < size() && get(i).equals(o)));
        throw new ContractException();
    }
}