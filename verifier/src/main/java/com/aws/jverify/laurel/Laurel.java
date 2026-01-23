package com.aws.jverify.laurel;

public class Laurel {
    public static LaurelType intType() { return new IntType(SourceRange.NONE); }
    public static LaurelType boolType() { return new BoolType(SourceRange.NONE); }
    public static LaurelType arrayType(LaurelType elemType) { return new ArrayType(SourceRange.NONE, elemType); }
    public static LaurelType compositeType(java.lang.String name) { return new CompositeType(SourceRange.NONE, name); }
    public static StmtExpr literalBool(boolean b) { return new LiteralBool(SourceRange.NONE, b); }
    public static StmtExpr int_(long n) { if (n < 0) throw new IllegalArgumentException("n must be non-negative"); return new Int(SourceRange.NONE, java.math.BigInteger.valueOf(n)); }
    public static OptionalType optionalType(LaurelType varType) { return new OptionalType_(SourceRange.NONE, varType); }
    public static OptionalAssignment optionalAssignment(StmtExpr value) { return new OptionalAssignment_(SourceRange.NONE, value); }
    public static StmtExpr varDecl(java.lang.String name, java.util.Optional<OptionalType> varType, java.util.Optional<OptionalAssignment> assignment) { return new VarDecl(SourceRange.NONE, name, varType, assignment); }
    public static StmtExpr call(StmtExpr callee, java.util.List<StmtExpr> args) { return new Call(SourceRange.NONE, callee, args); }
    public static StmtExpr fieldAccess(StmtExpr obj, java.lang.String field) { return new FieldAccess(SourceRange.NONE, obj, field); }
    public static StmtExpr arrayIndex(StmtExpr arr, StmtExpr idx) { return new ArrayIndex(SourceRange.NONE, arr, idx); }
    public static StmtExpr identifier(java.lang.String name) { return new Identifier(SourceRange.NONE, name); }
    public static StmtExpr parenthesis(StmtExpr inner) { return new Parenthesis(SourceRange.NONE, inner); }
    public static StmtExpr assign(StmtExpr target, StmtExpr value) { return new Assign(SourceRange.NONE, target, value); }
    public static StmtExpr add(StmtExpr lhs, StmtExpr rhs) { return new Add(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr sub(StmtExpr lhs, StmtExpr rhs) { return new Sub(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr mul(StmtExpr lhs, StmtExpr rhs) { return new Mul(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr div(StmtExpr lhs, StmtExpr rhs) { return new Div(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr mod(StmtExpr lhs, StmtExpr rhs) { return new Mod(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr divT(StmtExpr lhs, StmtExpr rhs) { return new DivT(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr modT(StmtExpr lhs, StmtExpr rhs) { return new ModT(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr eq(StmtExpr lhs, StmtExpr rhs) { return new Eq(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr neq(StmtExpr lhs, StmtExpr rhs) { return new Neq(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr gt(StmtExpr lhs, StmtExpr rhs) { return new Gt(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr lt(StmtExpr lhs, StmtExpr rhs) { return new Lt(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr le(StmtExpr lhs, StmtExpr rhs) { return new Le(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr ge(StmtExpr lhs, StmtExpr rhs) { return new Ge(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr and(StmtExpr lhs, StmtExpr rhs) { return new And(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr or(StmtExpr lhs, StmtExpr rhs) { return new Or(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr implies(StmtExpr lhs, StmtExpr rhs) { return new Implies(SourceRange.NONE, lhs, rhs); }
    public static StmtExpr not(StmtExpr inner) { return new Not(SourceRange.NONE, inner); }
    public static StmtExpr neg(StmtExpr inner) { return new Neg(SourceRange.NONE, inner); }
    public static StmtExpr forallExpr(java.lang.String name, LaurelType ty, StmtExpr body) { return new ForallExpr(SourceRange.NONE, name, ty, body); }
    public static StmtExpr existsExpr(java.lang.String name, LaurelType ty, StmtExpr body) { return new ExistsExpr(SourceRange.NONE, name, ty, body); }
    public static OptionalElse optionalElse(StmtExpr stmts) { return new OptionalElse_(SourceRange.NONE, stmts); }
    public static StmtExpr ifThenElse(StmtExpr cond, StmtExpr thenBranch, java.util.Optional<OptionalElse> elseBranch) { return new IfThenElse(SourceRange.NONE, cond, thenBranch, elseBranch); }
    public static StmtExpr assert_(StmtExpr cond) { return new Assert(SourceRange.NONE, cond); }
    public static StmtExpr assume(StmtExpr cond) { return new Assume(SourceRange.NONE, cond); }
    public static StmtExpr return_(StmtExpr value) { return new Return(SourceRange.NONE, value); }
    public static StmtExpr block(java.util.List<StmtExpr> stmts) { return new Block(SourceRange.NONE, stmts); }
    public static InvariantClause invariantClause(StmtExpr cond) { return new InvariantClause_(SourceRange.NONE, cond); }
    public static StmtExpr while_(StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) { return new While(SourceRange.NONE, cond, invariants, body); }
    public static Parameter parameter(java.lang.String name, LaurelType paramType) { return new Parameter_(SourceRange.NONE, name, paramType); }
    public static Field mutableField(java.lang.String name, LaurelType fieldType) { return new MutableField(SourceRange.NONE, name, fieldType); }
    public static Field immutableField(java.lang.String name, LaurelType fieldType) { return new ImmutableField(SourceRange.NONE, name, fieldType); }
    public static Composite composite(java.lang.String name, java.util.List<Field> fields) { return new Composite_(SourceRange.NONE, name, fields); }
    public static OptionalReturnType optionalReturnType(LaurelType returnType) { return new OptionalReturnType_(SourceRange.NONE, returnType); }
    public static RequiresClause requiresClause(StmtExpr cond) { return new RequiresClause_(SourceRange.NONE, cond); }
    public static EnsuresClause ensuresClause(StmtExpr cond) { return new EnsuresClause_(SourceRange.NONE, cond); }
    public static ReturnParameters returnParameters(java.util.List<Parameter> parameters) { return new ReturnParameters_(SourceRange.NONE, parameters); }
    public static Procedure procedure(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, StmtExpr body) { return new Procedure_(SourceRange.NONE, name, parameters, returnType, returnParameters, requires, ensures, body); }
    public static ConstrainedType constrainedType(java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) { return new ConstrainedType_(SourceRange.NONE, name, valueName, base, constraint, witness); }
    public static TopLevel topLevelComposite(Composite composite) { return new TopLevelComposite(SourceRange.NONE, composite); }
    public static TopLevel topLevelProcedure(Procedure procedure) { return new TopLevelProcedure(SourceRange.NONE, procedure); }
    public static TopLevel topLevelConstrainedType(ConstrainedType ct) { return new TopLevelConstrainedType(SourceRange.NONE, ct); }
    public static Command program(java.util.List<TopLevel> items) { return new Program(SourceRange.NONE, items); }
}
