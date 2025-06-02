package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 0, dafnyErrors = 0)
public class Polymorphism {
    public static void root() {
        var obj = new Object();
        GenericContainer<Object> container = new GenericContainer<>(obj);
        check(obj == container.getValue());
        var obj2 = genericIdentity(obj);
        check(obj == obj2);
    }
    
    @Pure
    public static <T> T genericIdentity(T a) {
        return a;
    }
}

class GenericContainer<T> {
    private T value;

    public GenericContainer(T value) {
        postcondition(this.value == value);
        this.value = value;
    }

    @Pure
    public T getValue() {
        return value;
    }
}

//void root() {
//    NumberContainer<Integer> intContainer = new NumberContainer<Integer>(42);
//    NumberContainer<Double> doubleContainer = new NumberContainer<Double>(3.14);
//    // check(intContainer.getDoubleValue() == 42.0);
//    // check(doubleContainer.getDoubleValue() == 3.14);
//
//    Integer maxInt = findMax(10, 20);
//    check(maxInt == 20);
//    // String not supported yet
//    // String maxString = findMax("apple", "banana");
//    // check(maxString.equals("banana"));
//
//    List<Integer> intList = List.of(1, 2);
//    double sum = sumCollection(intList);
//    // check(sum == 3);
//}
//
//
//@Verify(false)
//// Multiple type parameters with different constraints
//public static <T extends Collection<E>, E extends Number> double sumCollection(T collection) {
//    double sum = 0;
//    for (E element : collection) {
//        sum += element.doubleValue();
//    }
//    return sum;
//}

//@Contract(Drawable.class)
//class DrawableContract implements Drawable {
//    @Override
//    public void draw() {
//    }
//}
//
//interface Drawable {
//    void draw();
//}
//
//// T must implement both Drawable and Comparable
//class Shape<T extends Drawable & Comparable<T>> {
//    private T shape;
//
//    public void processShape(T other) {
//        shape.draw();                    // From Drawable
//        int comparison = shape.compareTo(other); // From Comparable
//    }
//}
//
//// T must be a Number or its subclass
//class NumberContainer<T extends Number> {
//    private final T value;
//
//    public NumberContainer(T value) {
//        this.value = value;
//    }
//
////    public double getDoubleValue() {
////        return value.doubleValue(); // Can call Number methods
////    }
//}
