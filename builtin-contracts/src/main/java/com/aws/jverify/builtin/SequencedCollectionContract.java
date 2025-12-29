package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import java.util.Collection;
import java.util.SequencedCollection;

@Contract(SequencedCollection.class)
interface SequencedCollectionContract<E> extends Collection<E> { }