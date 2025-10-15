package com.aws.jverify.examples;

public class MultiplyAllOpenJml {
    /**
     * Multiplies all elements in an array by a scalar value (in-place modification)
     * @param arr The array to modify
     * @param scalar The value to multiply each element by
     */
    //@ requires arr != null;
    //@ ensures arr.length == \old(arr.length);
    //@ ensures (\forall int i; 0 <= i && i < arr.length; arr[i] == \old(arr[i]) * scalar);
    //@ assignable arr[*];
    public static void multiplyAllByInPlace(int[] arr, int scalar) {
        //@ maintaining 0 <= i && i <= arr.length;
        //@ maintaining (\forall int j; 0 <= j && j < i; arr[j] == \old(arr[j]) * scalar);
        //@ maintaining (\forall int j; i <= j && j < arr.length; arr[j] == \old(arr[j]));
        //@ decreases arr.length - i;
        //@ assignable arr[*];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = arr[i] * scalar;
        }
    }
}



