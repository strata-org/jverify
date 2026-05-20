package org.strata.jverify.laurel;

import com.amazon.ion.IonSexp;

public sealed interface Node permits Parameter, AssignTarget, ReturnType, ConstrainedType, Composite, Procedure, Datatype, DatatypeConstructorList, DatatypeConstructorArg, ElseBranch, OpaqueSpec, Body, Extends, InvariantClause, Trigger, Initializer, Field, StmtExpr, Command, LaurelType, ErrorSummary, DatatypeConstructor, InvokeOnClause, RequiresClause, ReturnParameters, TypeAnnotation, ModifiesClause, EnsuresClause {
    SourceRange sourceRange();
    java.lang.String operationName();
    IonSexp toIon(IonSerializer $s);
}
