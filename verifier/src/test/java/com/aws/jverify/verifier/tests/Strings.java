package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({
        "ConstantValue",
        "SizeReplaceableByIsEmpty",
        "OnlyOneElementUsed",
        "StringOperationCanBeSimplified"
})
@JVerifyTest(dafnyVerified = 9, dafnyErrors = 0)
class Strings {
    static void stringConcat(String str) {
        check((str + str).length() == 2 * str.length());
    }

    static void stringCharAt(String str) {
        check(str.isEmpty() || str.charAt(0) >= '\0');
    }

    static void stringEquals() {
        check("hello world".equals("hello" + " " + "world"));
    }

    static void stringIsEmpty(String str) {
        check(str.length() > 0 || str.isEmpty());
    }

    static boolean stringLengthEven(String str) {
        return str.length() % 2 == 0;
    }

    static void stringSubstring() {
        check("hello world".substring(6).equals("world"));
        check("hello world".substring(4, 7).equals("o w"));
    }

    static void stringLiteralPrintableAscii() {
        check("hello".length() == "world".length());
        check("'\"\\".length() + 1 == "\0\n\r\t".length());
    }

    static void stringLiteralControlChars() {
        check("\u0000".charAt(0) == '\0');

        var s = "\u0001\u0002\u0003";
        check(s.length() >= 3);
        check(s.charAt(1) == 2);
    }

    static void stringLiteralMispairedSurrogates() {
        // two high surrogates
        check("\uD800\uDBFF".charAt(0) + 1
                // low followed by high
                == "\uDFFF\uD801".charAt(1));
        // two low surrogates
        check("\uDC00\uDFFF".charAt(1) - 1
                // low followed by high
                == "\uDFFE\uD800".charAt(0));
    }
}
