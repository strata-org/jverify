package org.strata.jverify.builtin;

import org.strata.jverify.*;
import java.util.List;
import static org.strata.jverify.JVerify.*;

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

    @Override
    @Pure
    public List<E> subList(@Nat int fromIndex, @Nat int toIndex) {
        reads(everything());
        decreases(0);
        precondition(0 <= fromIndex && fromIndex <= toIndex && toIndex <= size());
        // The sublist has length toIndex-fromIndex and its element i is
        // the receiver's element fromIndex+i. Mirrors the JBMC List model's
        // element-array view (java_bytecode_axiomatic.cpp LIST_GET).
        postcondition((List<E> r) ->
                r.size() == toIndex - fromIndex &&
                JVerify.forall((int i) ->
                        i < 0 || i >= r.size() || r.get(i) == get(fromIndex + i)));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean equals(Object o) {
        reads(everything());
        decreases(0);
        // Extensional list equality: same length and pointwise-equal
        // elements. Element comparison uses JVerify.jequals (structural
        // equality for pure element types, reference equality for impure
        // ones) to match java.util.List#equals, rather than `==`, which
        // would model reference equality and be unsound for
        // distinct-but-equal elements.
        postcondition((boolean r) ->
                r == (o instanceof List<?> other &&
                        other.size() == size() &&
                        JVerify.forall((int i) ->
                                i < 0 || i >= size() || JVerify.jequals(get(i), other.get(i)))));
        throw new ContractException();
    }
}
