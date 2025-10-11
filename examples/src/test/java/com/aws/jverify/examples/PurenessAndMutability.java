package com.aws.jverify.examples;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

/**
 * Interfaces are pure by default
 * Because the reads clause of get is abstract,
 * This interface can be implemented by both mutable and immutables types
 */
interface Container<T> {
    default T get() {
        decreases(0);
        reads(isAbstract());
        throw new ContractException();
    }
}

/**
 * Interfaces are pure by default
 */
interface ImmutableContainer<T> extends Container<T> {
    @Pure
    @Override
    default T get() {
        // no reads clause means its empty, 
        // which means that for a particular instance, 
        // the result of this method is constant
        throw new ContractException();
    }
}

/**
 * Interfaces are pure by default
 */
interface MutableContainer<T> extends Container<T> {
    default void set(T value) {
        // Copy the reads clause of get
        modifies(readsOf(get()));
        throw new ContractException();
    }
}

/**
 * Records are pure by default, so they can be instantiated in a pure context
 */
record ImmutableContainerImpl<T>(T value) implements ImmutableContainer<T> {
    @Pure
    @Override
    public T get() {
        decreases(0);
        // no reads clause - means its empty
        return value; // because value is final, it may be read without reads permissions
    }

    @Pure
    public static <T> ImmutableContainerImpl<T> createMe(T value) {
        return new ImmutableContainerImpl<T>(value);
    }
}

/**
 * Classes are impure by default, so they can only be created in an impure context.
 */
class MutableContainerImpl<T> implements MutableContainer<T> {
    private T value;

    @Pure
    @Override
    public T get() {
        decreases(0);
        reads(this);
        return value;
    }

    public void set(T value) {
        modifies(this);
        this.value = value;
    }

    @Pure
    public static <T> MutableContainer<T> createMe() {
        return new MutableContainerImpl<T>();
//             ^ error: using 'new' in an expression to create an instance of an impure type is not supported
    }
}