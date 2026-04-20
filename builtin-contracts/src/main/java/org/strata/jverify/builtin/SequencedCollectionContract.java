package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import java.util.Collection;
import java.util.SequencedCollection;

@Contract(SequencedCollection.class)
interface SequencedCollectionContract<E> extends Collection<E> { }