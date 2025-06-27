package com.aws.jverify.verifier.tests;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class Wildcards {

    public static List<Method> findMethod(Class<?> type, String methodName) {
//                                        ^ error: type com.sun.tools.javac.code.Type$WildcardType is not supported
        return null;
    }
}

