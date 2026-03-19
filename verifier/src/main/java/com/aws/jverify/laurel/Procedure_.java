package com.aws.jverify.laurel;

public record Procedure_(
    SourceRange sourceRange,
    java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<OptionalReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, java.util.Optional<OptionalBody> body
) implements Procedure {
    @Override
    public java.lang.String operationName() { return "Laurel.procedure"; }
}
