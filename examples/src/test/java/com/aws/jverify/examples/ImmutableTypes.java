package com.aws.jverify.examples;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

interface Container<T> {
    T get();
    
    @Contract
    class ContainerContract<T> implements Container<T> {
        @Pure
        @Override
        public T get() {
            reads(everything()); // we use reads(everything()) so implementing methods can still decide whether to be mutable or not
            decreases(0);
            throw new ContractException();
        }
    }
}

/**
 * Since an interface can not be instantiated in any context, 
 * JVerify does not care whether it's mutable or not.
 */
interface ImmutableContainer<T> extends Container<T> {
    @Contract
    class ImmutableContainerContract<T> implements ImmutableContainer<T> {
        @Pure
        @Override
        public T get() {
            // no reads clause - means its empty
            throw new ContractException();
        }
    }
}

/**
 * Immutable so it can be instantiated in a pure context
 */
record ImmutableContainerImpl<T>(T value) implements ImmutableContainer<T> {
    @Pure
    @Override
    public T get() {
        decreases(0);
        // no reads clause - means its empty
        return value;
    }
    
    @Pure
    public static <T> ImmutableContainerImpl<T> createMe(T value) {
        return new ImmutableContainerImpl<T>(value);
    }
}

/**
 * Mutable so it can not be instantiated in a pure context
 */
class MutableContainer<T> implements Container<T> {
    T value;

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
        return new MutableContainer<T>();
//             ^ error: using 'new' in an expression to create an instance of a mutable type is not supported
    }
}