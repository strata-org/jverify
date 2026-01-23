package com.aws.jverify.laurel;

public sealed interface Node permits Parameter, Field, ConstrainedType, StmtExpr, Command, LaurelType, Procedure, Composite, TopLevel, OptionalType, RequiresClause, OptionalReturnType, ReturnParameters, OptionalAssignment, InvariantClause, OptionalElse, EnsuresClause {
    SourceRange sourceRange();
    java.lang.String operationName();
}
