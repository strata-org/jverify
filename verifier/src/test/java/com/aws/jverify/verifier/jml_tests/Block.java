package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 4, dafnyErrors = 0)
public class Block {
    private /*@ spec_public @*/ int[] contents; 

    /*@ public normal_behavior
      @   ensures \fresh(contents);
      @ pure */
    public Block() {
      postcondition(fresh(contents));
      contents = new int[10];
    }

    /*@ public normal_behavior
      @   requires cont != null;
      @   ensures contents == cont;
      @ pure
      @*/
    public Block(int[] cont) {
//    precondition(cont != null); TODO: arrays should be nullable - this precondition currently adds a warning
      postcondition(contents == cont);
        contents = cont;
    }

    /*@ public normal_behavior
      @   requires size >= 0;
      @   ensures \fresh(\result);
      @   ensures \fresh(\result.contents);
      @*/
    public static Block allocate(int size) {
        precondition(size>=0);
        postcondition((Block result) -> fresh(result) && fresh(result.contents));
        int[] cont = new int[size];
        return new Block(cont);
    }
}