package org.strata.jverify.builtin;

import org.strata.jverify.*;
import java.util.List;
import static org.strata.jverify.JVerify.*;

abstract class ImmutableList<E> implements List<E> {
    @Override
    @Pure
    @Verify(false)
    public int size() {
        decreases(0);
        throw new ContractException();
    }

    @Override
    @Pure
    @Verify(false)
    public E get(@Nat int index) {
        precondition(0 <= index && index < size());
        throw new ContractException();
    }
}