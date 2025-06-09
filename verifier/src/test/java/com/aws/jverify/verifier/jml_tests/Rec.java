package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 3, dafnyErrors = 0)
public class Rec {

// This does not prove -- it seems that the solver has a limit on how 
// much it will unroll the recursive definition.

static void m(int i) {
  // @ assert countDown(0) == 0;
  check(countDown(0) == 0);
  // @ assert countDown(10) == 0;
  check(countDown(10) == 0);
  // @ assert countDown(20) == 0;
  check(countDown(20) == 0);
  // @ assert countDown(21) == 0;
  check(countDown(21) == 0);
  // @ assert countDown(25) == countDown(10);
  check(countDown(25) == countDown(10));
  //@ assert countDown(25) == 0;
  check(countDown(25) == 0);

}

/*@
 requires c >= 0;
 ensures c == 0 ==> \result == 0;
 ensures c > 0 ==> \result == countDown(c-1);
 @*/
 @Pure
static /*@ pure @*/int countDown(final int c) {
  precondition(c >= 0);
  return (c>0?countDown(c-1):0);
}

/*@
 requires c >= 0;
 ensures \result == 0;
 @*/
 @Pure
static /*@ pure @*/int countDownA(final int c) {
  precondition(c >= 0);
  return (c>0?countDownA(c-1):0);
}

}
