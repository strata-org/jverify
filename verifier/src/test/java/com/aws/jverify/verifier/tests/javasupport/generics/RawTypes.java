package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.HashMap;
import java.util.function.Supplier;

@JVerifyTest(dafnyVerified = 16, dafnyErrors = 0)
public class RawTypes {
    interface T {}

    Supplier<HashMap<T, T>> asMap() {
        return HashMap::new;
    }
    
    @Contract(HashMap.class)
    static abstract class HashMapContract<K, V> {
        public HashMapContract() {}
    } 
}
