package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.Collection;
import java.util.List;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(dafnyVerified = 0, dafnyErrors = 0)
public class Polymorphism {
    void root() {
        NumberContainer<Integer> intContainer = new NumberContainer<>(42);
        NumberContainer<Double> doubleContainer = new NumberContainer<>(3.14);
        check(intContainer.getDoubleValue() == 42.0);
        check(doubleContainer.getDoubleValue() == 3.14);

        Integer maxInt = findMax(10, 20);
        check(maxInt == 20);
        // String not supported yet
        // String maxString = findMax("apple", "banana");
        // check(maxString.equals("banana"));

        List<Integer> intList = List.of(1, 2, 3, 4, 5);
        double sum = sumCollection(intList);
        check(sum == 15);
    }

    // Method with bounded type parameter
    public static <T extends Comparable<T>> T findMax(T a, T b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    // Multiple type parameters with different constraints
    public static <T extends Collection<E>, E extends Number>
    double sumCollection(T collection) {
        double sum = 0;
        for (E element : collection) {
            sum += element.doubleValue();
        }
        return sum;
    }
}

@Contract(Drawable.class)
class DrawableContract implements Drawable {
    @Override
    public void draw() {
    }
}

interface Drawable {
    void draw();
    
}

// T must implement both Drawable and Comparable
class Shape<T extends Drawable & Comparable<T>> {
    private T shape;

    public void processShape(T other) {
        shape.draw();                    // From Drawable
        int comparison = shape.compareTo(other); // From Comparable
    }
}

// T must be a Number or its subclass
class NumberContainer<T extends Number> {
    private final T value;

    public NumberContainer(T value) {
        this.value = value;
    }

    public double getDoubleValue() {
        return value.doubleValue(); // Can call Number methods
    }
}
