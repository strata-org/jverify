package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.Invariant;
import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;

class UserProfile {
  void divZero() {
    var x = 10 / 0;
  }
}

// TEST: exitCode=4 dafnyVerified=5 dafnyErrors=1
