package org.strata.jverify.laurel;

import com.amazon.ion.IonSexp;

public sealed interface Node permits ThrowsOnClause, Parameter, AssignTarget, ReturnType, ConstrainedType, Composite, Procedure, Datatype, CatchGuard, DatatypeConstructorList, ThrowsClause, DatatypeConstructorArg, ElseBranch, OpaqueSpec, Body, Extends, InvariantClause, CatchClause, Trigger, Initializer, Field, EntryClause, StmtExpr, Command, LaurelType, ThrowsOnSpecClause, ErrorSummary, DatatypeConstructor, TypeParams, InvokeOnClause, RequiresClause, ReturnParameters, FinallyClause, TypeAnnotation, ModifiesClause, EnsuresClause {
    SourceRange sourceRange();
    java.lang.String operationName();
    IonSexp toIon(IonSerializer $s);
}
