package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Stream;

@JVerifyTest(dafnyVerified = 11, dafnyErrors = 0)
public class RawTypes {
    interface IT {}

    Map.Entry<Object, Object>[] asMap(Stream<Map.Entry<String, IT>> entries) {
        return entries.<Map.Entry<Object, Object>>toArray(Map.Entry[]::new);
    }
    
    @Contract(Stream.class)
    class StreamContract<T> {
        public <A> A[] toArray(IntFunction<A[]> generator) {
            throw new ContractException();
        }
    }
}
