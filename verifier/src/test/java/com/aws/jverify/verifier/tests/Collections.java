package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.IntSummaryStatistics;
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

@JVerifyTest(dafnyVerified = 0, dafnyErrors = 0)
public class Collections {

    void Foo() {
        IntStream s  = IntStream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }
}

@Contract(IntStream.class)
class IntStreamContract implements IntStream {

    @Override
    public IntStream filter(IntPredicate predicate) {
        throw new ContractException();
    }

    @Override
    public IntStream map(IntUnaryOperator mapper) {
        throw new ContractException();
    }

    @Override
    public Stream mapToObj(IntFunction mapper) {
        throw new ContractException();
    }

    @Override
    public LongStream mapToLong(IntToLongFunction mapper) {
        throw new ContractException();
    }

    @Override
    public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
        throw new ContractException();
    }

    @Override
    public IntStream flatMap(IntFunction mapper) {
        throw new ContractException();
    }

    @Override
    public IntStream distinct() {
        throw new ContractException();
    }

    @Override
    public IntStream sorted() {
        throw new ContractException();
    }

    @Override
    public IntStream peek(IntConsumer action) {
        throw new ContractException();
    }

    @Override
    public IntStream limit(long maxSize) {
        throw new ContractException();
    }

    @Override
    public IntStream skip(long n) {
        throw new ContractException();
    }

    @Override
    public void forEach(IntConsumer action) {
        throw new ContractException();
    }

    @Override
    public void forEachOrdered(IntConsumer action) {
        throw new ContractException();
    }

    @Override
    public int[] toArray() {
        throw new ContractException();
    }

    @Override
    public int reduce(int identity, IntBinaryOperator op) {
        throw new ContractException();
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator op) {
        throw new ContractException();
    }

    @Override
    public Object collect(Supplier supplier, ObjIntConsumer accumulator, BiConsumer combiner) {
        throw new ContractException();
    }

    @Override
    public int sum() {
        throw new ContractException();
    }

    @Override
    public OptionalInt min() {
        throw new ContractException();
    }

    @Override
    public OptionalInt max() {
        throw new ContractException();
    }

    @Override
    public long count() {
        throw new ContractException();
    }

    @Override
    public OptionalDouble average() {
        throw new ContractException();
    }

    @Override
    public IntSummaryStatistics summaryStatistics() {
        throw new ContractException();
    }

    @Override
    public boolean anyMatch(IntPredicate predicate) {
        throw new ContractException();
    }

    @Override
    public boolean allMatch(IntPredicate predicate) {
        throw new ContractException();
    }

    @Override
    public boolean noneMatch(IntPredicate predicate) {
        throw new ContractException();
    }

    @Override
    public OptionalInt findFirst() {
        throw new ContractException();
    }

    @Override
    public OptionalInt findAny() {
        throw new ContractException();
    }

    @Override
    public LongStream asLongStream() {
        throw new ContractException();
    }

    @Override
    public DoubleStream asDoubleStream() {
        throw new ContractException();
    }

    @Override
    public Stream boxed() {
        throw new ContractException();
    }

    @Override
    public IntStream sequential() {
        throw new ContractException();
    }

    @Override
    public IntStream parallel() {
        throw new ContractException();
    }

    @Override
    public IntStream unordered() {
        throw new ContractException();
    }

    @Override
    public IntStream onClose(Runnable closeHandler) {
        throw new ContractException();
    }

    @Override
    public void close() {
        throw new ContractException();
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        throw new ContractException();
    }

    @Override
    public Spliterator.OfInt spliterator() {
        throw new ContractException();
    }

    @Override
    public boolean isParallel() {
        throw new ContractException();
    }
}