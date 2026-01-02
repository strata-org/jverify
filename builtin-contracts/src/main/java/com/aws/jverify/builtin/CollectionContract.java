package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import java.util.Collection;
import java.util.stream.Stream;
import static com.aws.jverify.JVerify.*;

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