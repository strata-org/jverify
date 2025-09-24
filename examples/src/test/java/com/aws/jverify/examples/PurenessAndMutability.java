package com.aws.jverify.examples;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

/**
 * This container could be immutable or mutable, we don't know
 */
interface Container<T> {
    default T get() {
        // we use reads(everything()) so implementing methods can still decide whether to be mutable or not
        reads(everything());
        decreases(0);
        throw new ContractException();
    }
}

interface ImmutableContainer<T> extends Container<T> {
    @Pure
    @Override
    default T get() {
        // no reads clause means its empty, 
        // which means the result of this method for a particular instance, can not be modified 
        throw new ContractException();
    }
}

@Impure
interface MutableContainer<T> extends Container<T> {
    default void set(T value) {
        modifies(this); // referring to 'this' is only allowed because this interface is impure
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
        reads(this); // refines the inherited reads clause from 'all' to 'this'
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