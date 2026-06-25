package org.strata.jverify.laurel;

public class Laurel {
    public static LaurelType intType(SourceRange sourceRange) { return new LaurelType.IntType(sourceRange); }
    public static LaurelType intType() { return new LaurelType.IntType(SourceRange.NONE); }

    public static LaurelType boolType(SourceRange sourceRange) { return new LaurelType.BoolType(sourceRange); }
    public static LaurelType boolType() { return new LaurelType.BoolType(SourceRange.NONE); }

    public static LaurelType realType(SourceRange sourceRange) { return new LaurelType.RealType(sourceRange); }
    public static LaurelType realType() { return new LaurelType.RealType(SourceRange.NONE); }

    public static LaurelType float64Type(SourceRange sourceRange) { return new LaurelType.Float64Type(sourceRange); }
    public static LaurelType float64Type() { return new LaurelType.Float64Type(SourceRange.NONE); }

    public static LaurelType stringType(SourceRange sourceRange) { return new LaurelType.StringType(sourceRange); }
    public static LaurelType stringType() { return new LaurelType.StringType(SourceRange.NONE); }

    public static LaurelType bvType(SourceRange sourceRange, long width) { if (width < 0) throw new IllegalArgumentException("width must be non-negative"); return new LaurelType.BvType(sourceRange, java.math.BigInteger.valueOf(width)); }
    public static LaurelType bvType(long width) { if (width < 0) throw new IllegalArgumentException("width must be non-negative"); return new LaurelType.BvType(SourceRange.NONE, java.math.BigInteger.valueOf(width)); }

    public static LaurelType coreType(SourceRange sourceRange, java.lang.String name) { return new LaurelType.CoreType(sourceRange, name); }
    public static LaurelType coreType(java.lang.String name) { return new LaurelType.CoreType(SourceRange.NONE, name); }

    public static LaurelType mapType(SourceRange sourceRange, LaurelType keyType, LaurelType valueType) { return new LaurelType.MapType(sourceRange, keyType, valueType); }
    public static LaurelType mapType(LaurelType keyType, LaurelType valueType) { return new LaurelType.MapType(SourceRange.NONE, keyType, valueType); }

    public static LaurelType compositeType(SourceRange sourceRange, java.lang.String name) { return new LaurelType.CompositeType(sourceRange, name); }
    public static LaurelType compositeType(java.lang.String name) { return new LaurelType.CompositeType(SourceRange.NONE, name); }

    public static StmtExpr literalBool(SourceRange sourceRange, boolean b) { return new StmtExpr.LiteralBool(sourceRange, b); }
    public static StmtExpr literalBool(boolean b) { return new StmtExpr.LiteralBool(SourceRange.NONE, b); }

    public static StmtExpr int_(SourceRange sourceRange, long n) { if (n < 0) throw new IllegalArgumentException("n must be non-negative"); return new StmtExpr.Int(sourceRange, java.math.BigInteger.valueOf(n)); }
    public static StmtExpr int_(long n) { if (n < 0) throw new IllegalArgumentException("n must be non-negative"); return new StmtExpr.Int(SourceRange.NONE, java.math.BigInteger.valueOf(n)); }

    public static StmtExpr real(SourceRange sourceRange, double d) { return new StmtExpr.Real(sourceRange, java.math.BigDecimal.valueOf(d)); }
    public static StmtExpr real(double d) { return new StmtExpr.Real(SourceRange.NONE, java.math.BigDecimal.valueOf(d)); }

    public static StmtExpr string(SourceRange sourceRange, java.lang.String s) { return new StmtExpr.String_(sourceRange, s); }
    public static StmtExpr string(java.lang.String s) { return new StmtExpr.String_(SourceRange.NONE, s); }

    public static StmtExpr bvLiteral(SourceRange sourceRange, long value, long width) { if (value < 0) throw new IllegalArgumentException("value must be non-negative"); if (width < 0) throw new IllegalArgumentException("width must be non-negative"); return new StmtExpr.BvLiteral(sourceRange, java.math.BigInteger.valueOf(value), java.math.BigInteger.valueOf(width)); }
    public static StmtExpr bvLiteral(long value, long width) { if (value < 0) throw new IllegalArgumentException("value must be non-negative"); if (width < 0) throw new IllegalArgumentException("width must be non-negative"); return new StmtExpr.BvLiteral(SourceRange.NONE, java.math.BigInteger.valueOf(value), java.math.BigInteger.valueOf(width)); }

    public static StmtExpr hole(SourceRange sourceRange) { return new StmtExpr.Hole(sourceRange); }
    public static StmtExpr hole() { return new StmtExpr.Hole(SourceRange.NONE); }

    public static StmtExpr nondetHole(SourceRange sourceRange) { return new StmtExpr.NondetHole(sourceRange); }
    public static StmtExpr nondetHole() { return new StmtExpr.NondetHole(SourceRange.NONE); }

    public static TypeAnnotation typeAnnotation(SourceRange sourceRange, LaurelType varType) { return new TypeAnnotation.Of(sourceRange, varType); }
    public static TypeAnnotation typeAnnotation(LaurelType varType) { return new TypeAnnotation.Of(SourceRange.NONE, varType); }

    public static Initializer initializer(SourceRange sourceRange, StmtExpr value) { return new Initializer.Of(sourceRange, value); }
    public static Initializer initializer(StmtExpr value) { return new Initializer.Of(SourceRange.NONE, value); }

    public static StmtExpr varDecl(SourceRange sourceRange, java.lang.String name, java.util.Optional<TypeAnnotation> varType, java.util.Optional<Initializer> assignment) { return new StmtExpr.VarDecl(sourceRange, name, varType, assignment); }
    public static StmtExpr varDecl(java.lang.String name, java.util.Optional<TypeAnnotation> varType, java.util.Optional<Initializer> assignment) { return new StmtExpr.VarDecl(SourceRange.NONE, name, varType, assignment); }

    public static StmtExpr call(SourceRange sourceRange, StmtExpr callee, java.util.List<StmtExpr> args) { return new StmtExpr.Call(sourceRange, callee, args); }
    public static StmtExpr call(StmtExpr callee, java.util.List<StmtExpr> args) { return new StmtExpr.Call(SourceRange.NONE, callee, args); }

    public static StmtExpr new_(SourceRange sourceRange, java.lang.String name) { return new StmtExpr.New(sourceRange, name); }
    public static StmtExpr new_(java.lang.String name) { return new StmtExpr.New(SourceRange.NONE, name); }

    public static StmtExpr fieldAccess(SourceRange sourceRange, StmtExpr obj, java.lang.String field) { return new StmtExpr.FieldAccess(sourceRange, obj, field); }
    public static StmtExpr fieldAccess(StmtExpr obj, java.lang.String field) { return new StmtExpr.FieldAccess(SourceRange.NONE, obj, field); }

    public static StmtExpr identifier(SourceRange sourceRange, java.lang.String name) { return new StmtExpr.Identifier(sourceRange, name); }
    public static StmtExpr identifier(java.lang.String name) { return new StmtExpr.Identifier(SourceRange.NONE, name); }

    public static StmtExpr parenthesis(SourceRange sourceRange, StmtExpr inner) { return new StmtExpr.Parenthesis(sourceRange, inner); }
    public static StmtExpr parenthesis(StmtExpr inner) { return new StmtExpr.Parenthesis(SourceRange.NONE, inner); }

    public static StmtExpr assign(SourceRange sourceRange, StmtExpr target, StmtExpr value) { return new StmtExpr.Assign(sourceRange, target, value); }
    public static StmtExpr assign(StmtExpr target, StmtExpr value) { return new StmtExpr.Assign(SourceRange.NONE, target, value); }

    public static AssignTarget assignTargetDecl(SourceRange sourceRange, java.lang.String name, LaurelType targetType) { return new AssignTarget.AssignTargetDecl(sourceRange, name, targetType); }
    public static AssignTarget assignTargetDecl(java.lang.String name, LaurelType targetType) { return new AssignTarget.AssignTargetDecl(SourceRange.NONE, name, targetType); }

    public static AssignTarget assignTargetVar(SourceRange sourceRange, java.lang.String name) { return new AssignTarget.AssignTargetVar(sourceRange, name); }
    public static AssignTarget assignTargetVar(java.lang.String name) { return new AssignTarget.AssignTargetVar(SourceRange.NONE, name); }

    public static AssignTarget assignTargetField(SourceRange sourceRange, java.lang.String obj, java.lang.String field) { return new AssignTarget.AssignTargetField(sourceRange, obj, field); }
    public static AssignTarget assignTargetField(java.lang.String obj, java.lang.String field) { return new AssignTarget.AssignTargetField(SourceRange.NONE, obj, field); }

    public static StmtExpr multiAssign(SourceRange sourceRange, java.util.List<AssignTarget> targets, StmtExpr value) { return new StmtExpr.MultiAssign(sourceRange, targets, value); }
    public static StmtExpr multiAssign(java.util.List<AssignTarget> targets, StmtExpr value) { return new StmtExpr.MultiAssign(SourceRange.NONE, targets, value); }

    public static StmtExpr add(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Add(sourceRange, lhs, rhs); }
    public static StmtExpr add(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Add(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr sub(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Sub(sourceRange, lhs, rhs); }
    public static StmtExpr sub(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Sub(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr mul(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Mul(sourceRange, lhs, rhs); }
    public static StmtExpr mul(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Mul(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr div(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Div(sourceRange, lhs, rhs); }
    public static StmtExpr div(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Div(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr mod(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Mod(sourceRange, lhs, rhs); }
    public static StmtExpr mod(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Mod(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr divT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.DivT(sourceRange, lhs, rhs); }
    public static StmtExpr divT(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.DivT(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr modT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.ModT(sourceRange, lhs, rhs); }
    public static StmtExpr modT(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.ModT(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr eq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Eq(sourceRange, lhs, rhs); }
    public static StmtExpr eq(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Eq(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr neq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Neq(sourceRange, lhs, rhs); }
    public static StmtExpr neq(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Neq(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr gt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Gt(sourceRange, lhs, rhs); }
    public static StmtExpr gt(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Gt(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr lt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Lt(sourceRange, lhs, rhs); }
    public static StmtExpr lt(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Lt(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr le(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Le(sourceRange, lhs, rhs); }
    public static StmtExpr le(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Le(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr ge(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Ge(sourceRange, lhs, rhs); }
    public static StmtExpr ge(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Ge(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr and(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.And(sourceRange, lhs, rhs); }
    public static StmtExpr and(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.And(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr or(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Or(sourceRange, lhs, rhs); }
    public static StmtExpr or(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Or(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr andThen(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.AndThen(sourceRange, lhs, rhs); }
    public static StmtExpr andThen(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.AndThen(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr orElse(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.OrElse(sourceRange, lhs, rhs); }
    public static StmtExpr orElse(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.OrElse(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr implies(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Implies(sourceRange, lhs, rhs); }
    public static StmtExpr implies(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.Implies(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr strConcat(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.StrConcat(sourceRange, lhs, rhs); }
    public static StmtExpr strConcat(StmtExpr lhs, StmtExpr rhs) { return new StmtExpr.StrConcat(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr not(SourceRange sourceRange, StmtExpr inner) { return new StmtExpr.Not(sourceRange, inner); }
    public static StmtExpr not(StmtExpr inner) { return new StmtExpr.Not(SourceRange.NONE, inner); }

    public static StmtExpr neg(SourceRange sourceRange, StmtExpr inner) { return new StmtExpr.Neg(sourceRange, inner); }
    public static StmtExpr neg(StmtExpr inner) { return new StmtExpr.Neg(SourceRange.NONE, inner); }

    public static StmtExpr preIncr(SourceRange sourceRange, StmtExpr target) { return new StmtExpr.PreIncr(sourceRange, target); }
    public static StmtExpr preIncr(StmtExpr target) { return new StmtExpr.PreIncr(SourceRange.NONE, target); }

    public static StmtExpr preDecr(SourceRange sourceRange, StmtExpr target) { return new StmtExpr.PreDecr(sourceRange, target); }
    public static StmtExpr preDecr(StmtExpr target) { return new StmtExpr.PreDecr(SourceRange.NONE, target); }

    public static StmtExpr postIncr(SourceRange sourceRange, StmtExpr target) { return new StmtExpr.PostIncr(sourceRange, target); }
    public static StmtExpr postIncr(StmtExpr target) { return new StmtExpr.PostIncr(SourceRange.NONE, target); }

    public static StmtExpr postDecr(SourceRange sourceRange, StmtExpr target) { return new StmtExpr.PostDecr(sourceRange, target); }
    public static StmtExpr postDecr(StmtExpr target) { return new StmtExpr.PostDecr(SourceRange.NONE, target); }

    public static Trigger trigger(SourceRange sourceRange, StmtExpr trigger) { return new Trigger.Of(sourceRange, trigger); }
    public static Trigger trigger(StmtExpr trigger) { return new Trigger.Of(SourceRange.NONE, trigger); }

    public static StmtExpr forallExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body) { return new StmtExpr.ForallExpr(sourceRange, name, ty, trigger, body); }
    public static StmtExpr forallExpr(java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body) { return new StmtExpr.ForallExpr(SourceRange.NONE, name, ty, trigger, body); }

    public static StmtExpr existsExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body) { return new StmtExpr.ExistsExpr(sourceRange, name, ty, trigger, body); }
    public static StmtExpr existsExpr(java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body) { return new StmtExpr.ExistsExpr(SourceRange.NONE, name, ty, trigger, body); }

    public static ErrorSummary errorSummary(SourceRange sourceRange, java.lang.String msg) { return new ErrorSummary.Of(sourceRange, msg); }
    public static ErrorSummary errorSummary(java.lang.String msg) { return new ErrorSummary.Of(SourceRange.NONE, msg); }

    public static ElseBranch elseBranch(SourceRange sourceRange, StmtExpr stmts) { return new ElseBranch.Of(sourceRange, stmts); }
    public static ElseBranch elseBranch(StmtExpr stmts) { return new ElseBranch.Of(SourceRange.NONE, stmts); }

    public static StmtExpr ifThenElse(SourceRange sourceRange, StmtExpr cond, StmtExpr thenBranch, java.util.Optional<ElseBranch> elseBranch) { return new StmtExpr.IfThenElse(sourceRange, cond, thenBranch, elseBranch); }
    public static StmtExpr ifThenElse(StmtExpr cond, StmtExpr thenBranch, java.util.Optional<ElseBranch> elseBranch) { return new StmtExpr.IfThenElse(SourceRange.NONE, cond, thenBranch, elseBranch); }

    public static StmtExpr assert_(SourceRange sourceRange, StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new StmtExpr.Assert(sourceRange, cond, errorMessage); }
    public static StmtExpr assert_(StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new StmtExpr.Assert(SourceRange.NONE, cond, errorMessage); }

    public static StmtExpr assume(SourceRange sourceRange, StmtExpr cond) { return new StmtExpr.Assume(sourceRange, cond); }
    public static StmtExpr assume(StmtExpr cond) { return new StmtExpr.Assume(SourceRange.NONE, cond); }

    public static StmtExpr return_(SourceRange sourceRange, java.util.Optional<StmtExpr> value) { return new StmtExpr.Return(sourceRange, value); }
    public static StmtExpr return_(java.util.Optional<StmtExpr> value) { return new StmtExpr.Return(SourceRange.NONE, value); }

    public static StmtExpr block(SourceRange sourceRange, java.util.List<StmtExpr> stmts) { return new StmtExpr.Block(sourceRange, stmts); }
    public static StmtExpr block(java.util.List<StmtExpr> stmts) { return new StmtExpr.Block(SourceRange.NONE, stmts); }

    public static StmtExpr labelledBlock(SourceRange sourceRange, java.util.List<StmtExpr> stmts, java.lang.String label) { return new StmtExpr.LabelledBlock(sourceRange, stmts, label); }
    public static StmtExpr labelledBlock(java.util.List<StmtExpr> stmts, java.lang.String label) { return new StmtExpr.LabelledBlock(SourceRange.NONE, stmts, label); }

    public static StmtExpr exit(SourceRange sourceRange, java.lang.String label) { return new StmtExpr.Exit(sourceRange, label); }
    public static StmtExpr exit(java.lang.String label) { return new StmtExpr.Exit(SourceRange.NONE, label); }

    public static InvariantClause invariantClause(SourceRange sourceRange, StmtExpr cond) { return new InvariantClause.Of(sourceRange, cond); }
    public static InvariantClause invariantClause(StmtExpr cond) { return new InvariantClause.Of(SourceRange.NONE, cond); }

    public static StmtExpr while_(SourceRange sourceRange, StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) { return new StmtExpr.While(sourceRange, cond, invariants, body); }
    public static StmtExpr while_(StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) { return new StmtExpr.While(SourceRange.NONE, cond, invariants, body); }

    public static StmtExpr forLoop(SourceRange sourceRange, StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body) { return new StmtExpr.ForLoop(sourceRange, init, cond, step, invariants, body); }
    public static StmtExpr forLoop(StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body) { return new StmtExpr.ForLoop(SourceRange.NONE, init, cond, step, invariants, body); }

    public static Parameter parameter(SourceRange sourceRange, java.lang.String name, LaurelType paramType) { return new Parameter.Of(sourceRange, name, paramType); }
    public static Parameter parameter(java.lang.String name, LaurelType paramType) { return new Parameter.Of(SourceRange.NONE, name, paramType); }

    public static Field mutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) { return new Field.MutableField(sourceRange, name, fieldType); }
    public static Field mutableField(java.lang.String name, LaurelType fieldType) { return new Field.MutableField(SourceRange.NONE, name, fieldType); }

    public static Field immutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) { return new Field.ImmutableField(sourceRange, name, fieldType); }
    public static Field immutableField(java.lang.String name, LaurelType fieldType) { return new Field.ImmutableField(SourceRange.NONE, name, fieldType); }

    public static StmtExpr isType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) { return new StmtExpr.IsType(sourceRange, target, typeName); }
    public static StmtExpr isType(StmtExpr target, java.lang.String typeName) { return new StmtExpr.IsType(SourceRange.NONE, target, typeName); }

    public static StmtExpr asType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) { return new StmtExpr.AsType(sourceRange, target, typeName); }
    public static StmtExpr asType(StmtExpr target, java.lang.String typeName) { return new StmtExpr.AsType(SourceRange.NONE, target, typeName); }

    public static Extends extends_(SourceRange sourceRange, java.util.List<java.lang.String> parents) { return new Extends.Of(sourceRange, parents); }
    public static Extends extends_(java.util.List<java.lang.String> parents) { return new Extends.Of(SourceRange.NONE, parents); }

    public static DatatypeConstructorArg datatypeConstructorArg(SourceRange sourceRange, java.lang.String name, LaurelType argType) { return new DatatypeConstructorArg.Of(sourceRange, name, argType); }
    public static DatatypeConstructorArg datatypeConstructorArg(java.lang.String name, LaurelType argType) { return new DatatypeConstructorArg.Of(SourceRange.NONE, name, argType); }

    public static DatatypeConstructor datatypeConstructor(SourceRange sourceRange, java.lang.String name, java.util.List<DatatypeConstructorArg> args) { return new DatatypeConstructor.DatatypeConstructor_(sourceRange, name, args); }
    public static DatatypeConstructor datatypeConstructor(java.lang.String name, java.util.List<DatatypeConstructorArg> args) { return new DatatypeConstructor.DatatypeConstructor_(SourceRange.NONE, name, args); }

    public static DatatypeConstructor datatypeConstructorNoArgs(SourceRange sourceRange, java.lang.String name) { return new DatatypeConstructor.DatatypeConstructorNoArgs(sourceRange, name); }
    public static DatatypeConstructor datatypeConstructorNoArgs(java.lang.String name) { return new DatatypeConstructor.DatatypeConstructorNoArgs(SourceRange.NONE, name); }

    public static DatatypeConstructorList datatypeConstructorList(SourceRange sourceRange, java.util.List<DatatypeConstructor> constructors) { return new DatatypeConstructorList.Of(sourceRange, constructors); }
    public static DatatypeConstructorList datatypeConstructorList(java.util.List<DatatypeConstructor> constructors) { return new DatatypeConstructorList.Of(SourceRange.NONE, constructors); }

    public static Datatype datatype(SourceRange sourceRange, java.lang.String name, DatatypeConstructorList constructors) { return new Datatype.Of(sourceRange, name, constructors); }
    public static Datatype datatype(java.lang.String name, DatatypeConstructorList constructors) { return new Datatype.Of(SourceRange.NONE, name, constructors); }

    public static ReturnType returnType(SourceRange sourceRange, LaurelType returnType) { return new ReturnType.Of(sourceRange, returnType); }
    public static ReturnType returnType(LaurelType returnType) { return new ReturnType.Of(SourceRange.NONE, returnType); }

    public static InvokeOnClause invokeOnClause(SourceRange sourceRange, StmtExpr trigger) { return new InvokeOnClause.Of(sourceRange, trigger); }
    public static InvokeOnClause invokeOnClause(StmtExpr trigger) { return new InvokeOnClause.Of(SourceRange.NONE, trigger); }

    public static RequiresClause requiresClause(SourceRange sourceRange, StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new RequiresClause.Of(sourceRange, cond, errorMessage); }
    public static RequiresClause requiresClause(StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new RequiresClause.Of(SourceRange.NONE, cond, errorMessage); }

    public static EnsuresClause ensuresClause(SourceRange sourceRange, StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new EnsuresClause.Of(sourceRange, cond, errorMessage); }
    public static EnsuresClause ensuresClause(StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage) { return new EnsuresClause.Of(SourceRange.NONE, cond, errorMessage); }

    public static ModifiesClause modifiesClause(SourceRange sourceRange, java.util.List<StmtExpr> refs) { return new ModifiesClause.ModifiesClause_(sourceRange, refs); }
    public static ModifiesClause modifiesClause(java.util.List<StmtExpr> refs) { return new ModifiesClause.ModifiesClause_(SourceRange.NONE, refs); }

    public static ModifiesClause modifiesWildcard(SourceRange sourceRange) { return new ModifiesClause.ModifiesWildcard(sourceRange); }
    public static ModifiesClause modifiesWildcard() { return new ModifiesClause.ModifiesWildcard(SourceRange.NONE); }

    public static ReturnParameters returnParameters(SourceRange sourceRange, java.util.List<Parameter> parameters) { return new ReturnParameters.Of(sourceRange, parameters); }
    public static ReturnParameters returnParameters(java.util.List<Parameter> parameters) { return new ReturnParameters.Of(SourceRange.NONE, parameters); }

    public static OpaqueSpec opaqueSpec(SourceRange sourceRange, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies) { return new OpaqueSpec.Of(sourceRange, ensures, modifies); }
    public static OpaqueSpec opaqueSpec(java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies) { return new OpaqueSpec.Of(SourceRange.NONE, ensures, modifies); }

    public static Body body(SourceRange sourceRange, StmtExpr body) { return new Body.Body_(sourceRange, body); }
    public static Body body(StmtExpr body) { return new Body.Body_(SourceRange.NONE, body); }

    public static Body externalBody(SourceRange sourceRange) { return new Body.ExternalBody(sourceRange); }
    public static Body externalBody() { return new Body.ExternalBody(SourceRange.NONE); }

    public static Procedure procedure(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.Optional<InvokeOnClause> invokeOn, java.util.Optional<OpaqueSpec> opaqueSpec, java.util.Optional<Body> body) { return new Procedure.Procedure_(sourceRange, name, parameters, returnType, returnParameters, requires, invokeOn, opaqueSpec, body); }
    public static Procedure procedure(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.Optional<InvokeOnClause> invokeOn, java.util.Optional<OpaqueSpec> opaqueSpec, java.util.Optional<Body> body) { return new Procedure.Procedure_(SourceRange.NONE, name, parameters, returnType, returnParameters, requires, invokeOn, opaqueSpec, body); }

    public static Procedure function(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.Optional<InvokeOnClause> invokeOn, java.util.Optional<OpaqueSpec> opaqueSpec, java.util.Optional<Body> body) { return new Procedure.Function(sourceRange, name, parameters, returnType, returnParameters, requires, invokeOn, opaqueSpec, body); }
    public static Procedure function(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.Optional<InvokeOnClause> invokeOn, java.util.Optional<OpaqueSpec> opaqueSpec, java.util.Optional<Body> body) { return new Procedure.Function(SourceRange.NONE, name, parameters, returnType, returnParameters, requires, invokeOn, opaqueSpec, body); }

    public static Composite composite(SourceRange sourceRange, java.lang.String name, java.util.Optional<Extends> extending, java.util.List<Field> fields, java.util.List<Procedure> procedures) { return new Composite.Of(sourceRange, name, extending, fields, procedures); }
    public static Composite composite(java.lang.String name, java.util.Optional<Extends> extending, java.util.List<Field> fields, java.util.List<Procedure> procedures) { return new Composite.Of(SourceRange.NONE, name, extending, fields, procedures); }

    public static ConstrainedType constrainedType(SourceRange sourceRange, java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) { return new ConstrainedType.Of(sourceRange, name, valueName, base, constraint, witness); }
    public static ConstrainedType constrainedType(java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) { return new ConstrainedType.Of(SourceRange.NONE, name, valueName, base, constraint, witness); }

    public static Command compositeCommand(SourceRange sourceRange, Composite composite) { return new Command.CompositeCommand(sourceRange, composite); }
    public static Command compositeCommand(Composite composite) { return new Command.CompositeCommand(SourceRange.NONE, composite); }

    public static Command procedureCommand(SourceRange sourceRange, Procedure procedure) { return new Command.ProcedureCommand(sourceRange, procedure); }
    public static Command procedureCommand(Procedure procedure) { return new Command.ProcedureCommand(SourceRange.NONE, procedure); }

    public static Command datatypeCommand(SourceRange sourceRange, Datatype datatype) { return new Command.DatatypeCommand(sourceRange, datatype); }
    public static Command datatypeCommand(Datatype datatype) { return new Command.DatatypeCommand(SourceRange.NONE, datatype); }

    public static Command constrainedTypeCommand(SourceRange sourceRange, ConstrainedType ct) { return new Command.ConstrainedTypeCommand(sourceRange, ct); }
    public static Command constrainedTypeCommand(ConstrainedType ct) { return new Command.ConstrainedTypeCommand(SourceRange.NONE, ct); }
}
