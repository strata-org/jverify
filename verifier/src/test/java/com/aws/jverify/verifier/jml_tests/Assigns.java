package com.aws.jverify.verifier.jml_tests;
// One error per class to avoid non-deterministic output (reordering of errors)
import static com.aws.jverify.JVerify.*;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 23, dafnyErrors = 17)
public class Assigns {
  public static class T {
    public int v;
    public int w;
  }

  public Assigns() {
    postcondition(a.length == 10);
    t1 = new T();
    t2 = new T();
    a = new int[10];
  }

  public T t1;
  public T t2;
  public int x;
  public int[] a;

  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes \everything;
  public void m1() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this);
    modifies(this.t1);
    modifies(this.a);
    x = 0;
    t1.v = 0;
    a[1] = 0;
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.v;
  public void m2a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    t1.v = 0;
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.v;
  public void m2b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    t2.v = 0; // ERROR
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.v;
  public void m2c() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    t1.v = 0;
    a[1] = 0; // ERROR
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[1];
  public void m3a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[1];
  public void m3b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    t1.v = 0; // ERROR
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[1];
  public void m3c() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    a[1] = 0;
    a[4] = 0; // ERROR
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[*];
  public void m4a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[*];
  public void m4b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    t1.v = 0; // ERROR
//  ^^^^ Error: assignment might update an object not in the enclosing context's modifies clause
    a[1] = 0;
    a[4] = 0;
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[3..5];
  public void m5a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[3..5];
  public void m5b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a);
    t1.v = 0; // ERROR
//  ^^^^ Error: assignment might update an object not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes a[3..5];
  public void m5c() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.a); // TODO: being able to specify only partial array write?
    a[4] = 0;
    a[1] = 0; // ERROR - not raised by JVerify for now
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.*;
  public void m6a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause
    t1.v = 0;
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.*;
  public void m6b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    t1.v = 0;
    a[4] = 0; // ERROR
//  ^^^^ Error: assignment might update an array element not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes t1.*;
  public void m6c() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    modifies(this.t1);
    t1.v = 0;
    a[1] = 0; // ERROR
//  ^^^^ Error: assignment might update an array element not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes \nothing;
  public void m7a() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    x = 0; // ERROR
//  ^ Error: assignment might update an object not in the enclosing context's modifies clause

  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes \nothing;
  public void m7b() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    t1.v = 0; // ERROR
//  ^^^^ Error: assignment might update an object not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes \nothing;
  public void m7c() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    a[4] = 0; // ERROR
//  ^^^^ Error: assignment might update an array element not in the enclosing context's modifies clause
  }
  //@ requires t1 != t2;
  //@ requires a.length == 10;
  //@ writes \nothing;
  public void m7d() {
    precondition(t1 != t2);
    precondition(a.length == 10);
    a[1] = 0; // ERROR
//  ^^^^ Error: assignment might update an array element not in the enclosing context's modifies clause
  }
}
