package com.aws.jverify.laurel;

public class Laurel {
    public static LaurelType intType(SourceRange sourceRange) { return new IntType(sourceRange); }
    public static LaurelType intType() { return new IntType(SourceRange.NONE); }

    public static LaurelType boolType(SourceRange sourceRange) { return new BoolType(sourceRange); }
    public static LaurelType boolType() { return new BoolType(SourceRange.NONE); }

    public static LaurelType realType(SourceRange sourceRange) { return new RealType(sourceRange); }
    public static LaurelType realType() { return new RealType(SourceRange.NONE); }

    public static LaurelType float64Type(SourceRange sourceRange) { return new Float64Type(sourceRange); }
    public static LaurelType float64Type() { return new Float64Type(SourceRange.NONE); }

    public static LaurelType stringType(SourceRange sourceRange) { return new StringType(sourceRange); }
    public static LaurelType stringType() { return new StringType(SourceRange.NONE); }

    public static LaurelType mapType(SourceRange sourceRange, LaurelType keyType, LaurelType valueType) { return new MapType(sourceRange, keyType, valueType); }
    public static LaurelType mapType(LaurelType keyType, LaurelType valueType) { return new MapType(SourceRange.NONE, keyType, valueType); }

    public static LaurelType compositeType(SourceRange sourceRange, java.lang.String name) { return new CompositeType(sourceRange, name); }
    public static LaurelType compositeType(java.lang.String name) { return new CompositeType(SourceRange.NONE, name); }

    public static StmtExpr literalBool(SourceRange sourceRange, boolean b) { return new LiteralBool(sourceRange, b); }
    public static StmtExpr literalBool(boolean b) { return new LiteralBool(SourceRange.NONE, b); }

    public static StmtExpr int_(SourceRange sourceRange, long n) { if (n < 0) throw new IllegalArgumentException("n must be non-negative"); return new Int(sourceRange, java.math.BigInteger.valueOf(n)); }
    public static StmtExpr int_(long n) { if (n < 0) throw new IllegalArgumentException("n must be non-negative"); return new Int(SourceRange.NONE, java.math.BigInteger.valueOf(n)); }

    public static StmtExpr real(SourceRange sourceRange, double d) { return new Real(sourceRange, java.math.BigDecimal.valueOf(d)); }
    public static StmtExpr real(double d) { return new Real(SourceRange.NONE, java.math.BigDecimal.valueOf(d)); }

    public static StmtExpr string(SourceRange sourceRange, java.lang.String s) { return new String_(sourceRange, s); }
    public static StmtExpr string(java.lang.String s) { return new String_(SourceRange.NONE, s); }

    public static StmtExpr hole(SourceRange sourceRange) { return new Hole(sourceRange); }
    public static StmtExpr hole() { return new Hole(SourceRange.NONE); }

    public static StmtExpr nondetHole(SourceRange sourceRange) { return new NondetHole(sourceRange); }
    public static StmtExpr nondetHole() { return new NondetHole(SourceRange.NONE); }

    public static OptionalType optionalType(SourceRange sourceRange, LaurelType varType) { return new OptionalType_(sourceRange, varType); }
    public static OptionalType optionalType(LaurelType varType) { return new OptionalType_(SourceRange.NONE, varType); }

    public static OptionalAssignment optionalAssignment(SourceRange sourceRange, StmtExpr value) { return new OptionalAssignment_(sourceRange, value); }
    public static OptionalAssignment optionalAssignment(StmtExpr value) { return new OptionalAssignment_(SourceRange.NONE, value); }

    public static StmtExpr varDecl(SourceRange sourceRange, java.lang.String name, java.util.Optional<OptionalType> varType, java.util.Optional<OptionalAssignment> assignment) { return new VarDecl(sourceRange, name, varType, assignment); }
    public static StmtExpr varDecl(java.lang.String name, java.util.Optional<OptionalType> varType, java.util.Optional<OptionalAssignment> assignment) { return new VarDecl(SourceRange.NONE, name, varType, assignment); }

    public static StmtExpr call(SourceRange sourceRange, StmtExpr callee, java.util.List<StmtExpr> args) { return new Call(sourceRange, callee, args); }
    public static StmtExpr call(StmtExpr callee, java.util.List<StmtExpr> args) { return new Call(SourceRange.NONE, callee, args); }

    public static StmtExpr new_(SourceRange sourceRange, java.lang.String name) { return new New(sourceRange, name); }
    public static StmtExpr new_(java.lang.String name) { return new New(SourceRange.NONE, name); }

    public static StmtExpr fieldAccess(SourceRange sourceRange, StmtExpr obj, java.lang.String field) { return new FieldAccess(sourceRange, obj, field); }
    public static StmtExpr fieldAccess(StmtExpr obj, java.lang.String field) { return new FieldAccess(SourceRange.NONE, obj, field); }

    public static StmtExpr identifier(SourceRange sourceRange, java.lang.String name) { return new Identifier(sourceRange, name); }
    public static StmtExpr identifier(java.lang.String name) { return new Identifier(SourceRange.NONE, name); }

    public static StmtExpr parenthesis(SourceRange sourceRange, StmtExpr inner) { return new Parenthesis(sourceRange, inner); }
    public static StmtExpr parenthesis(StmtExpr inner) { return new Parenthesis(SourceRange.NONE, inner); }

    public static StmtExpr assign(SourceRange sourceRange, StmtExpr target, StmtExpr value) { return new Assign(sourceRange, target, value); }
    public static StmtExpr assign(StmtExpr target, StmtExpr value) { return new Assign(SourceRange.NONE, target, value); }

    public static StmtExpr add(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Add(sourceRange, lhs, rhs); }
    public static StmtExpr add(StmtExpr lhs, StmtExpr rhs) { return new Add(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr sub(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Sub(sourceRange, lhs, rhs); }
    public static StmtExpr sub(StmtExpr lhs, StmtExpr rhs) { return new Sub(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr mul(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Mul(sourceRange, lhs, rhs); }
    public static StmtExpr mul(StmtExpr lhs, StmtExpr rhs) { return new Mul(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr div(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Div(sourceRange, lhs, rhs); }
    public static StmtExpr div(StmtExpr lhs, StmtExpr rhs) { return new Div(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr mod(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Mod(sourceRange, lhs, rhs); }
    public static StmtExpr mod(StmtExpr lhs, StmtExpr rhs) { return new Mod(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr divT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new DivT(sourceRange, lhs, rhs); }
    public static StmtExpr divT(StmtExpr lhs, StmtExpr rhs) { return new DivT(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr modT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new ModT(sourceRange, lhs, rhs); }
    public static StmtExpr modT(StmtExpr lhs, StmtExpr rhs) { return new ModT(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr eq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Eq(sourceRange, lhs, rhs); }
    public static StmtExpr eq(StmtExpr lhs, StmtExpr rhs) { return new Eq(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr neq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Neq(sourceRange, lhs, rhs); }
    public static StmtExpr neq(StmtExpr lhs, StmtExpr rhs) { return new Neq(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr gt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Gt(sourceRange, lhs, rhs); }
    public static StmtExpr gt(StmtExpr lhs, StmtExpr rhs) { return new Gt(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr lt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Lt(sourceRange, lhs, rhs); }
    public static StmtExpr lt(StmtExpr lhs, StmtExpr rhs) { return new Lt(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr le(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Le(sourceRange, lhs, rhs); }
    public static StmtExpr le(StmtExpr lhs, StmtExpr rhs) { return new Le(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr ge(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Ge(sourceRange, lhs, rhs); }
    public static StmtExpr ge(StmtExpr lhs, StmtExpr rhs) { return new Ge(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr and(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new And(sourceRange, lhs, rhs); }
    public static StmtExpr and(StmtExpr lhs, StmtExpr rhs) { return new And(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr or(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Or(sourceRange, lhs, rhs); }
    public static StmtExpr or(StmtExpr lhs, StmtExpr rhs) { return new Or(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr andThen(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new AndThen(sourceRange, lhs, rhs); }
    public static StmtExpr andThen(StmtExpr lhs, StmtExpr rhs) { return new AndThen(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr orElse(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new OrElse(sourceRange, lhs, rhs); }
    public static StmtExpr orElse(StmtExpr lhs, StmtExpr rhs) { return new OrElse(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr implies(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new Implies(sourceRange, lhs, rhs); }
    public static StmtExpr implies(StmtExpr lhs, StmtExpr rhs) { return new Implies(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr strConcat(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) { return new StrConcat(sourceRange, lhs, rhs); }
    public static StmtExpr strConcat(StmtExpr lhs, StmtExpr rhs) { return new StrConcat(SourceRange.NONE, lhs, rhs); }

    public static StmtExpr not(SourceRange sourceRange, StmtExpr inner) { return new Not(sourceRange, inner); }
    public static StmtExpr not(StmtExpr inner) { return new Not(SourceRange.NONE, inner); }

    public static StmtExpr neg(SourceRange sourceRange, StmtExpr inner) { return new Neg(sourceRange, inner); }
    public static StmtExpr neg(StmtExpr inner) { return new Neg(SourceRange.NONE, inner); }

    public static OptionalTrigger optionalTrigger(SourceRange sourceRange, StmtExpr trigger) { return new OptionalTrigger_(sourceRange, trigger); }
    public static OptionalTrigger optionalTrigger(StmtExpr trigger) { return new OptionalTrigger_(SourceRange.NONE, trigger); }

    public static StmtExpr forallExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, java.util.Optional<OptionalTrigger> trigger, StmtExpr body) { return new ForallExpr(sourceRange, name, ty, trigger, body); }
    public static StmtExpr forallExpr(java.lang.String name, LaurelType ty, java.util.Optional<OptionalTrigger> trigger, StmtExpr body) { return new ForallExpr(SourceRange.NONE, name, ty, trigger, body); }

    public static StmtExpr existsExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, java.util.Optional<OptionalTrigger> trigger, StmtExpr body) { return new ExistsExpr(sourceRange, name, ty, trigger, body); }
    public static StmtExpr existsExpr(java.lang.String name, LaurelType ty, java.util.Optional<OptionalTrigger> trigger, StmtExpr body) { return new ExistsExpr(SourceRange.NONE, name, ty, trigger, body); }

    public static OptionalErrorMessage errorMessage(SourceRange sourceRange, java.lang.String msg) { return new ErrorMessage(sourceRange, msg); }
    public static OptionalErrorMessage errorMessage(java.lang.String msg) { return new ErrorMessage(SourceRange.NONE, msg); }

    public static OptionalElse optionalElse(SourceRange sourceRange, StmtExpr stmts) { return new OptionalElse_(sourceRange, stmts); }
    public static OptionalElse optionalElse(StmtExpr stmts) { return new OptionalElse_(SourceRange.NONE, stmts); }

    public static StmtExpr ifThenElse(SourceRange sourceRange, StmtExpr cond, StmtExpr thenBranch, java.util.Optional<OptionalElse> elseBranch) { return new IfThenElse(sourceRange, cond, thenBranch, elseBranch); }
    public static StmtExpr ifThenElse(StmtExpr cond, StmtExpr thenBranch, java.util.Optional<OptionalElse> elseBranch) { return new IfThenElse(SourceRange.NONE, cond, thenBranch, elseBranch); }

    public static StmtExpr assert_(SourceRange sourceRange, StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new Assert(sourceRange, cond, errorMessage); }
    public static StmtExpr assert_(StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new Assert(SourceRange.NONE, cond, errorMessage); }

    public static StmtExpr assume(SourceRange sourceRange, StmtExpr cond) { return new Assume(sourceRange, cond); }
    public static StmtExpr assume(StmtExpr cond) { return new Assume(SourceRange.NONE, cond); }

    public static StmtExpr return_(SourceRange sourceRange, StmtExpr value) { return new Return(sourceRange, value); }
    public static StmtExpr return_(StmtExpr value) { return new Return(SourceRange.NONE, value); }

    public static StmtExpr block(SourceRange sourceRange, java.util.List<StmtExpr> stmts) { return new Block(sourceRange, stmts); }
    public static StmtExpr block(java.util.List<StmtExpr> stmts) { return new Block(SourceRange.NONE, stmts); }

    public static InvariantClause invariantClause(SourceRange sourceRange, StmtExpr cond) { return new InvariantClause_(sourceRange, cond); }
    public static InvariantClause invariantClause(StmtExpr cond) { return new InvariantClause_(SourceRange.NONE, cond); }

    public static StmtExpr while_(SourceRange sourceRange, StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) { return new While(sourceRange, cond, invariants, body); }
    public static StmtExpr while_(StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) { return new While(SourceRange.NONE, cond, invariants, body); }

    public static StmtExpr forLoop(SourceRange sourceRange, StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body) { return new ForLoop(sourceRange, init, cond, step, invariants, body); }
    public static StmtExpr forLoop(StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body) { return new ForLoop(SourceRange.NONE, init, cond, step, invariants, body); }

    public static Parameter parameter(SourceRange sourceRange, java.lang.String name, LaurelType paramType) { return new Parameter_(sourceRange, name, paramType); }
    public static Parameter parameter(java.lang.String name, LaurelType paramType) { return new Parameter_(SourceRange.NONE, name, paramType); }

    public static Field mutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) { return new MutableField(sourceRange, name, fieldType); }
    public static Field mutableField(java.lang.String name, LaurelType fieldType) { return new MutableField(SourceRange.NONE, name, fieldType); }

    public static Field immutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) { return new ImmutableField(sourceRange, name, fieldType); }
    public static Field immutableField(java.lang.String name, LaurelType fieldType) { return new ImmutableField(SourceRange.NONE, name, fieldType); }

    public static StmtExpr isType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) { return new IsType(sourceRange, target, typeName); }
    public static StmtExpr isType(StmtExpr target, java.lang.String typeName) { return new IsType(SourceRange.NONE, target, typeName); }

    public static StmtExpr asType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) { return new AsType(sourceRange, target, typeName); }
    public static StmtExpr asType(StmtExpr target, java.lang.String typeName) { return new AsType(SourceRange.NONE, target, typeName); }

    public static OptionalExtends optionalExtends(SourceRange sourceRange, java.util.List<java.lang.String> parents) { return new OptionalExtends_(sourceRange, parents); }
    public static OptionalExtends optionalExtends(java.util.List<java.lang.String> parents) { return new OptionalExtends_(SourceRange.NONE, parents); }

    public static Composite composite(SourceRange sourceRange, java.lang.String name, java.util.Optional<OptionalExtends> extending, java.util.List<Field> fields) { return new Composite_(sourceRange, name, extending, fields); }
    public static Composite composite(java.lang.String name, java.util.Optional<OptionalExtends> extending, java.util.List<Field> fields) { return new Composite_(SourceRange.NONE, name, extending, fields); }

    public static DatatypeConstructorArg datatypeConstructorArg(SourceRange sourceRange, java.lang.String name, LaurelType argType) { return new DatatypeConstructorArg_(sourceRange, name, argType); }
    public static DatatypeConstructorArg datatypeConstructorArg(java.lang.String name, LaurelType argType) { return new DatatypeConstructorArg_(SourceRange.NONE, name, argType); }

    public static DatatypeConstructor datatypeConstructor(SourceRange sourceRange, java.lang.String name, java.util.List<DatatypeConstructorArg> args) { return new DatatypeConstructor_(sourceRange, name, args); }
    public static DatatypeConstructor datatypeConstructor(java.lang.String name, java.util.List<DatatypeConstructorArg> args) { return new DatatypeConstructor_(SourceRange.NONE, name, args); }

    public static DatatypeConstructor datatypeConstructorNoArgs(SourceRange sourceRange, java.lang.String name) { return new DatatypeConstructorNoArgs(sourceRange, name); }
    public static DatatypeConstructor datatypeConstructorNoArgs(java.lang.String name) { return new DatatypeConstructorNoArgs(SourceRange.NONE, name); }

    public static DatatypeConstructorList datatypeConstructorList(SourceRange sourceRange, java.util.List<DatatypeConstructor> constructors) { return new DatatypeConstructorList_(sourceRange, constructors); }
    public static DatatypeConstructorList datatypeConstructorList(java.util.List<DatatypeConstructor> constructors) { return new DatatypeConstructorList_(SourceRange.NONE, constructors); }

    public static Datatype datatype(SourceRange sourceRange, java.lang.String name, DatatypeConstructorList constructors) { return new Datatype_(sourceRange, name, constructors); }
    public static Datatype datatype(java.lang.String name, DatatypeConstructorList constructors) { return new Datatype_(SourceRange.NONE, name, constructors); }

    public static OptionalReturnType optionalReturnType(SourceRange sourceRange, LaurelType returnType) { return new OptionalReturnType_(sourceRange, returnType); }
    public static OptionalReturnType optionalReturnType(LaurelType returnType) { return new OptionalReturnType_(SourceRange.NONE, returnType); }

    public static RequiresClause requiresClause(SourceRange sourceRange, StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new RequiresClause_(sourceRange, cond, errorMessage); }
    public static RequiresClause requiresClause(StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new RequiresClause_(SourceRange.NONE, cond, errorMessage); }

    public static EnsuresClause ensuresClause(SourceRange sourceRange, StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new EnsuresClause_(sourceRange, cond, errorMessage); }
    public static EnsuresClause ensuresClause(StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage) { return new EnsuresClause_(SourceRange.NONE, cond, errorMessage); }

    public static ModifiesClause modifiesClause(SourceRange sourceRange, java.util.List<StmtExpr> refs) { return new ModifiesClause_(sourceRange, refs); }
    public static ModifiesClause modifiesClause(java.util.List<StmtExpr> refs) { return new ModifiesClause_(SourceRange.NONE, refs); }

    public static ReturnParameters returnParameters(SourceRange sourceRange, java.util.List<Parameter> parameters) { return new ReturnParameters_(sourceRange, parameters); }
    public static ReturnParameters returnParameters(java.util.List<Parameter> parameters) { return new ReturnParameters_(SourceRange.NONE, parameters); }

    public static OptionalBody optionalBody(SourceRange sourceRange, StmtExpr body) { return new OptionalBody_(sourceRange, body); }
    public static OptionalBody optionalBody(StmtExpr body) { return new OptionalBody_(SourceRange.NONE, body); }

    public static OptionalBody externalBody(SourceRange sourceRange) { return new ExternalBody(sourceRange); }
    public static OptionalBody externalBody() { return new ExternalBody(SourceRange.NONE); }

    public static Procedure procedure(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, java.util.Optional<OptionalBody> body) { return new Procedure_(sourceRange, name, parameters, returnType, returnParameters, requires, ensures, modifies, body); }
    public static Procedure procedure(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, java.util.Optional<OptionalBody> body) { return new Procedure_(SourceRange.NONE, name, parameters, returnType, returnParameters, requires, ensures, modifies, body); }

    public static Procedure function(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, java.util.Optional<OptionalBody> body) { return new Function(sourceRange, name, parameters, returnType, returnParameters, requires, ensures, modifies, body); }
    public static Procedure function(java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, java.util.Optional<OptionalBody> body) { return new Function(SourceRange.NONE, name, parameters, returnType, returnParameters, requires, ensures, modifies, body); }

    public static ConstrainedType constrainedType(SourceRange sourceRange, java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) { return new ConstrainedType_(sourceRange, name, valueName, base, constraint, witness); }
    public static ConstrainedType constrainedType(java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) { return new ConstrainedType_(SourceRange.NONE, name, valueName, base, constraint, witness); }

    public static Command compositeCommand(SourceRange sourceRange, Composite composite) { return new CompositeCommand(sourceRange, composite); }
    public static Command compositeCommand(Composite composite) { return new CompositeCommand(SourceRange.NONE, composite); }

    public static Command procedureCommand(SourceRange sourceRange, Procedure procedure) { return new ProcedureCommand(sourceRange, procedure); }
    public static Command procedureCommand(Procedure procedure) { return new ProcedureCommand(SourceRange.NONE, procedure); }

    public static Command datatypeCommand(SourceRange sourceRange, Datatype datatype) { return new DatatypeCommand(sourceRange, datatype); }
    public static Command datatypeCommand(Datatype datatype) { return new DatatypeCommand(SourceRange.NONE, datatype); }

    public static Command constrainedTypeCommand(SourceRange sourceRange, ConstrainedType ct) { return new ConstrainedTypeCommand(sourceRange, ct); }
    public static Command constrainedTypeCommand(ConstrainedType ct) { return new ConstrainedTypeCommand(SourceRange.NONE, ct); }
}
