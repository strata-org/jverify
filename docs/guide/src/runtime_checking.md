# Runtime checking of contracts

JVerify allows turning the preconditions of a method into runtime checks, using `@CheckPreconditionsAtRuntime` attribute. If such preconditions are not satisfied at runtime, a `PreconditionFailure` exception is thrown.

TODO: add examples