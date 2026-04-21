package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.util.List;

import java.util.ArrayList;

public interface Property<T> {
    T get();
    void set(T value);

    public static <T> Property<T> fromElement(ArrayList<T> list, int index) {
        return new Property<T>() {
            @Override
            public T get() {
                return list.get(index);
            }

            @Override
            public void set(T value) {
                list.set(index, value);
            }
        };
    }

    public static <T> Property<T> fromElement(com.sun.tools.javac.util.List<T> list, int index) {
        return new Property<T>() {
            @Override
            public T get() {
                return list.get(index);
            }

            @Override
            public void set(T value) {
                List<T> current = list;
                //noinspection StatementWithEmptyBody
                var i = 0;
                while (i < index) {
                    i++;
                    current = current.tail;
                }
                current.head = value;
            }
        };
    }

    public static <T> Property<T> finalProperty(T value) {
        return new Property<T>() {
            @Override
            public T get() {
                return value;
            }

            @Override
            public void set(T value) {
                throw new RuntimeException("can not mutate final property");
            }
        };
    }
}
