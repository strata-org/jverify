package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0)
public class Wildcards {
    
    boolean isNull(Container<?> elements) {
        return elements.isNull();
    }
    
    static void numberGetterUser(Container<Integer> ints) {
        numberGetter(ints);
    }
    
    static <T> double numberGetter(Container<? extends Number> numbers) {
        return numbers.get().doubleValue();
    }

    static void numberSetterUser(Container<Number> ints) {
        numberSetter(ints, 3.0);
    }
    
    static <T> void numberSetter(Container<? super Number> numberContainer, Double d) {
        numberContainer.set(d);
    }

    static class Container<T> {
        T value;

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
        
        public boolean isNull() {
            return value == null;
        }

    }
}
