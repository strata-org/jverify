package com.aws.jverify.laurel;

public sealed interface LaurelType extends Node permits IntType, BoolType, RealType, Float64Type, StringType, MapType, CompositeType {}
