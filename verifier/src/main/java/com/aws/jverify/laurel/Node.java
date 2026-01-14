package com.aws.jverify.laurel;

public sealed interface Node permits Parameter, ReturnParameters, StmtExpr, Command, LaurelType, OptionalAssignment, Procedure, OptionalElse, OptionalType {
    SourceRange sourceRange();
    java.lang.String operationName();
}
