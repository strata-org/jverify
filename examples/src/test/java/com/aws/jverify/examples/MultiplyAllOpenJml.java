package com.aws.jverify.examples;

public class MultiplyAllOpenJml {
    /*@ requires arr != null;
      @ requires (\forall int i; 0 <= i && i < arr.length; 
      @           arr[i] >= Integer.MIN_VALUE / scalar && 
      @           arr[i] <= Integer.MAX_VALUE / scalar);
      @ ensures arr != null;
      @ ensures arr.length == \old(arr.length);
      @ ensures (\forall int i; 0 <= i && i < arr.length; 
      @           arr[i] == \old(arr[i]) * scalar);
      @ assignable arr[*];
      @*/
    public static void multiplyAll(int[] arr, int scalar) {
        /*@ loop_invariant 0 <= i && i <= arr.length;
          @ loop_invariant (\forall int j; 0 <= j && j < i; 
          @                  arr[j] == \old(arr[j]) * scalar);
          @ loop_invariant (\forall int j; i <= j && j < arr.length; 
          @                  arr[j] == \old(arr[j]));
          @ loop_assigns i, arr[*];
          @ decreases arr.length - i;
          @*/
        for (int i = 0; i < arr.length; i++) {
            arr[i] = arr[i] * scalar;
        }
    }
}



