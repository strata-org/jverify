package com.aws.jverify.laurel;

public class Laurel {
    public static LaurelType intType() { return new IntType(SourceRange.NONE); }
    public static LaurelType boolType() { return new BoolType(SourceRange.NONE); }
    public static StmtExpr literalBool(boolean b) { return new LiteralBool(SourceRange.NONE, b); }
    public static StmtExpr int_(long n) { return new Int(SourceRange.NONE, java.math.BigInteger.valueOf(n)); }
    public static OptionalType optionalType(LaurelType varType) { return new OptionalType_(SourceRange.NONE, varType); }
    public static OptionalAssignment optionalAssignment(StmtExpr value) { return new OptionalAssignment_(SourceRange.NONE, value); }
    public static StmtExpr varDecl(java.lang.String name, java.util.Optional<OptionalType> varType, java.util.Optional<OptionalAssignment> assignment) { return new VarDecl(SourceRange.NONE, name, varType, assignment); }
    public static StmtExpr identifier(java.lang.String name) { return new Identifier(SourceRange.NONE, name); }
    public static StmtExpr parenthesis(StmtExpr inner) { return new Parenthesis(SourceRange.NONE, inner); }
    public static StmtExpr assign(StmtExpr target, StmtExpr value) { return new Assign(SourceRange.NONE, target, value); }
    public static StmtExpr add(StmtExpr lhs, StmtExpr rhs) { return new Add(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr eq(StmtExpr lhs, StmtExpr rhs) { return new Eq(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr neq(StmtExpr lhs, StmtExpr rhs) { return new Neq(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr gt(StmtExpr lhs, StmtExpr rhs) { return new Gt(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr lt(StmtExpr lhs, StmtExpr rhs) { return new Lt(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr le(StmtExpr lhs, StmtExpr rhs) { return new Le(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr ge(StmtExpr lhs, StmtExpr rhs) { return new Ge(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr call(StmtExpr callee, java.util.List<StmtExpr> args) { return new Call(SourceRange.NONE, callee, args); }
    public static OptionalElse optionalElse(StmtExpr stmts) { return new OptionalElse_(SourceRange.NONE, stmts); }
    public static StmtExpr ifThenElse(StmtExpr cond, StmtExpr thenBranch, java.util.Optional<OptionalElse> elseBranch) { return new IfThenElse(SourceRange.NONE, cond, thenBranch, elseBranch); }
    public static StmtExpr assert_(StmtExpr cond) { return new Assert(SourceRange.NONE, cond); }
    public static StmtExpr assume(StmtExpr cond) { return new Assume(SourceRange.NONE, cond); }
    public static StmtExpr return_(StmtExpr value) { return new Return(SourceRange.NONE, value); }
    public static StmtExpr block(java.util.List<StmtExpr> stmts) { return new Block(SourceRange.NONE, stmts); }
    public static Parameter parameter(java.lang.String name, LaurelType paramType) { return new Parameter_(SourceRange.NONE, name, paramType); }
    public static ReturnParameters returnParameters(java.util.List<Parameter> parameters) { return new ReturnParameters_(SourceRange.NONE, parameters); }
    public static Procedure procedure(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnParameters> returnParameters, StmtExpr body) { return new Procedure_(SourceRange.NONE, name, parameters, returnParameters, body); }
    public static Command program(java.util.List<Procedure> staticProcedures) { return new Program(SourceRange.NONE, staticProcedures); }
}
