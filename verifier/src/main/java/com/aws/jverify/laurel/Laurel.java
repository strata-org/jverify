package com.aws.jverify.laurel;

public class Laurel {
    public static StmtExpr literalBool(Expr b) { return new LiteralBool(SourceRange.NONE, b); }
    public static StmtExpr assert_(StmtExpr cond) { return new Assert(SourceRange.NONE, cond); }
    public static StmtExpr assume(StmtExpr cond) { return new Assume(SourceRange.NONE, cond); }
    public static StmtExpr block(java.util.List<StmtExpr> stmts) { return new Block(SourceRange.NONE, stmts); }
    public static Procedure procedure(java.lang.String name, StmtExpr body) { return new Procedure_(SourceRange.NONE, name, body); }
    public static Command program(java.util.List<Procedure> staticProcedures) { return new Program(SourceRange.NONE, staticProcedures); }
}
