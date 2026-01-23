package com.aws.jverify.laurel;

public sealed interface TopLevel extends Node permits TopLevelComposite, TopLevelProcedure, TopLevelConstrainedType {}
