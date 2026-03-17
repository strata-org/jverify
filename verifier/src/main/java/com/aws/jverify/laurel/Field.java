package com.aws.jverify.laurel;

public sealed interface Field extends Node permits MutableField, ImmutableField {}
