package com.aws.jverify.laurel;

public sealed interface Command extends Node permits CompositeCommand, ProcedureCommand, DatatypeCommand, ConstrainedTypeCommand {}
