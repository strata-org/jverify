package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import java.util.Optional;
import static com.aws.jverify.JVerify.*;

@Contract(value = Optional.class, pure = true)
class OptionalContract<T> {

    private T value;
    private boolean isSet;

    @Pure
    static <T> OptionalContract<T> of(T value) {
        postcondition((OptionalContract<T> o) -> o.value == value && o.isSet);
        throw new ContractException();
    }

    @Pure
    static <T> OptionalContract<T> empty() {
        postcondition((OptionalContract<T> o) -> !o.isSet);
        throw new ContractException();
    }

    //Currently the same as of because we are not able to handle null cases
    //TODO: add null check
    /*
    @Pure
    static <T> OptionalContract<T> ofNullable(T value) {
        postcondition((OptionalContract<T> o) ->  o.isSet && o.value == value);
        throw new ContractException();
    }
    */

    @Pure
    boolean isEmpty() {
        postcondition((boolean r) -> r == !isSet);
        throw new ContractException();
    }

    @Pure
    boolean isPresent() {
        postcondition((boolean r) -> r == isSet);
        throw new ContractException();
    }

    @Pure
    T orElse(T value) {
        postcondition((T b) -> isPresent()? b == this.value : b == value);
        throw new ContractException();
    }

    @Pure
    T get() {
        precondition(isSet);
        postcondition((T b) -> b == this.value);
        throw new ContractException();
    }
}