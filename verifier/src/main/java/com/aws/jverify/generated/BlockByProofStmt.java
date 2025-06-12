package com.aws.jverify.generated;

// Generated BlockByProofStmt.java:
// Generated from C# class
public class BlockByProofStmt extends Statement {
  private final BlockStmt proof;

  private final Statement body;

  public BlockByProofStmt(IOrigin origin, Attributes attributes, BlockStmt proof, Statement body) {
    super(origin, attributes);
    this.proof = proof;
    this.body = body;
  }

  public BlockStmt getProof() {
    return this.proof;
  }

  public Statement getBody() {
    return this.body;
  }
}
