package com.aws.jverify.builtin;

import com.aws.jverify.*;
import java.util.Set;
import static com.aws.jverify.JVerify.*;

@Contract(value = Set.class, pure = true)
abstract class SetContract<E> implements Set<E> {

    JVerify.Set<E> elements;

    @Pure
    static <E> Set<E> of() {
        postcondition((Set<E> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1) {
        postcondition((Set<E> r) ->
                r.size() == 1 && r.contains(e1));
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1, E e2) {
        postcondition((Set<E> r) ->
                r.size() == 2 &&
                        r.contains(e1) &&
                        r.contains(e2));
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1, E e2, E e3) {
        postcondition((Set<E> r) ->
                r.size() == 3 &&
                        r.contains(e1) &&
                        r.contains(e2) &&
                        r.contains(e3));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        decreases(0);
        postcondition((boolean r) ->
                r == JVerify.exists((E other) -> elements.contains(other) && other.equals(o)));
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        decreases(0);
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        return size() == 0;
    }
}