package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import java.util.Optional;
import static org.strata.jverify.JVerify.*;

@Contract(value = Optional.class, pure = true)
class OptionalContract<T> {

    private T value;
    private boolean isSet;

    static <T> OptionalContract<T> of(T value) {
        postcondition((OptionalContract<T> o) -> o.value == value && o.isSet);
        throw new ContractException();
    }

    static <T> OptionalContract<T> empty() {
        postcondition((OptionalContract<T> o) -> !o.isSet);
        throw new ContractException();
    }

    //Currently the same as of because we are not able to handle null cases
    //TODO: add null check
    /*
    static <T> OptionalContract<T> ofNullable(T value) {
        postcondition((OptionalContract<T> o) ->  o.isSet && o.value == value);
        throw new ContractException();
    }
    */

    boolean isEmpty() {
        postcondition((boolean r) -> r == !isSet);
        throw new ContractException();
    }

    boolean isPresent() {
        postcondition((boolean r) -> r == isSet);
        throw new ContractException();
    }

    T orElse(T value) {
        postcondition((T b) -> isPresent()? b == this.value : b == value);
        throw new ContractException();
    }

    T get() {
        precondition(isSet);
        postcondition((T b) -> b == this.value);
        throw new ContractException();
    }
}