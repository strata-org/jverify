package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import java.util.Collection;
import java.util.stream.Stream;
import static org.strata.jverify.JVerify.*;

@Contract(Collection.class)
abstract class CollectionContract<E> implements Collection<E> {

    @Pure
    public Stream<E> stream() {
        reads(everything());
        decreases(0);
        throw new ContractException();
    }
    
    @Override
    @Pure
    public int size() {
        reads(everything());
        decreases(0);
        throw new ContractException();
    }
}