package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.JVerify;
import org.strata.jverify.Nat;
import org.strata.jverify.Pure;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.strata.jverify.JVerify.cast;
import static org.strata.jverify.JVerify.decreases;
import static org.strata.jverify.JVerify.modifies;
import static org.strata.jverify.JVerify.old;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;
import static org.strata.jverify.JVerify.reads;

@Contract(ArrayList.class)
abstract class ArrayListContract<E> extends ArrayList<E> {

    protected JVerify.Sequence<E> elements;

    public ArrayListContract() {
        postcondition(elements.size() == 0);
    }

    @Pure
    public Stream<E> stream() {
        reads(this);
        decreases(0);
        postcondition((Stream<E> s) -> cast(s, StreamContract.class).elements == elements);
        throw new ContractException();
    }

    @Override
    @Pure
    public E get(@Nat int index) {
        reads(this);
        decreases(0);
        precondition(0 <= index && index < size());
        postcondition((E e) -> e == elements.get(index));
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        reads(this);
        decreases(0);
        postcondition((int s) -> s == elements.size());
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        reads(this);
        decreases(0);
        postcondition((boolean r) -> r == (elements.size() == 0));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        reads(this);
        decreases(size());
        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < elements.size() && elements.get(i).equals(o)));


        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < size() && get(i).equals(o)));
        throw new ContractException();
    }

    @Override
    public boolean add(E element) {
        modifies(this);
        postcondition((boolean r) -> elements == old(elements.concat(JVerify.elements(element))));
        throw new ContractException();
    }
}
