package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Contract;
import org.strata.jverify.testengine.JVerifyTest;

import java.util.HashMap;
import java.util.function.Supplier;

@JVerifyTest(methodsVerified = 2, errorCount = 0)
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
