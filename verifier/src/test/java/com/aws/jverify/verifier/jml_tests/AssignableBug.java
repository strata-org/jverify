package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 7, dafnyErrors = 0)
public class AssignableBug {

    public int xSize;
    
  public Piece piece;
  
  //@ requires pp.position.x >= 0 && pp.position.x < xSize;
  //@ ensures piece == pp;
  //@ pure
  public AssignableBug(Piece pp, int xSize) {
      precondition(pp.position.x >= 0 && pp.position.x < xSize);
      postcondition(piece == pp);
      piece = pp;
      this.xSize = xSize;
  }

  //@ public invariant piece.position.x >= 0 && piece.position.x < xSize;
  
  //@ normal_behavior
  //@ requires inRange(p);
  //@ assignable piece.position;
  public void test(Position p) {
   precondition(inRange(p));
   modifies(piece.position);
   modifies(piece);
    piece.setPosition(p);
    //@ assert inRange(piece.position);
  }
  
  //@ ensures \result == ( p.x >= 0 && p.x < xSize );
  //@ pure helper
  @Pure
  public boolean inRange(Position p) {
      reads(p);
      reads(this);
      return p.x >= 0 && p.x < xSize;
  }
}

class Piece {
   public Position position;

   //@ ensures position == p;
   //@ pure
   public Piece(Position p) {
      postcondition(position == p);
      position = p;
   }

   //@ normal_behavior
   //@ assignable position;
   //@ ensures position == p;
   public void setPosition(Position p) {
      modifies(this);
      postcondition(position == p);
      position = p;
   }
}

class Position {
   public int x;
}
