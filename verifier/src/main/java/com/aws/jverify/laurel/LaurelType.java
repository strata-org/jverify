package com.aws.jverify.laurel;

public sealed interface LaurelType extends Node permits IntType, BoolType, ArrayType, CompositeType {}
