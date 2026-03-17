package com.aws.jverify.laurel;

public sealed interface Node permits OptionalExtends, Parameter, ConstrainedType, Composite, Procedure, Datatype, DatatypeConstructorList, DatatypeConstructorArg, OptionalTrigger, InvariantClause, OptionalElse, OptionalErrorMessage, OptionalBody, Field, StmtExpr, LaurelType, Command, OptionalType, DatatypeConstructor, RequiresClause, OptionalReturnType, ReturnParameters, OptionalAssignment, ModifiesClause, EnsuresClause {
    SourceRange sourceRange();
    java.lang.String operationName();
}
