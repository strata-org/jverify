package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Erased;
import com.aws.jverify.JVerify;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 0, dafnyErrors = 0)
public class ListTest {

    void Foo() {
        var s  = List.of("one", "two", "three");
        check(s.size() == 3);
        check(s.get(1) == "two");
    }
}

@Contract
abstract class ListContract<E> implements List<E> {

    JVerify.Sequence<E> elements;

//    @Override
//    public boolean contains(Object o) {
//        postcondition((boolean r) -> r == elements.contains(o));
//    }

    static <E> List<E> of(E e1, E e2) {
        postcondition((List<E> r) ->
                r.get(0) == e1 &&
                        r.get(1) == e2 &&
                        r.size() == 2);
        throw new ContractException();
    }

    static <E> List<E> of(E e1, E e2, E e3) {
        postcondition((List<E> r) ->
                r.get(0) == e1 &&
                r.get(1) == e2 &&
                r.get(2) == e3 &&
                r.size() == 3);
        throw new ContractException();
    }

    @Override
    @Pure
    public E get(int index) {
        postcondition((E e) -> e == elements.get(index));
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        postcondition((Integer s) -> s == elements.size());
        throw new ContractException();
    }
}