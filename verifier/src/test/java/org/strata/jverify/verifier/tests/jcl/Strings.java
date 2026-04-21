// ^ /object-contract.java(37:19-37:61) Related location: this proposition could not be proved
// ^ /object-contract.java(71:16-71:33) Related location: this proposition could not be proved
// ^ /object-contract.java(91:16-91:37) Related location: this proposition could not be proved
// ^ /object-contract.java(91:16-91:37) Related location: this proposition could not be proved
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({
        "ConstantValue",
        "SizeReplaceableByIsEmpty",
        "OnlyOneElementUsed",
        "StringOperationCanBeSimplified"
})
@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 13, errorCount = 6)
class Strings {
    @SuppressWarnings("unused")
    static final String veryLongLiteral = "One thing, I don't know why. It doesn't even matter how hard you try. Keep that in mind, I designed this rhyme to explain in due time. All I know time is a valuable thing";
    
    static void stringConcat(String str) {
        check((str + str).length() == 2 * str.length());
    }

    static void stringCharAt(String str) {
        check(str.isEmpty() || str.charAt(0) >= '\0');
    }

    static void stringCharAtIncorrect() {
        check("🐱".charAt(0) == '\uD83D');
        check("🐱".charAt(0) == '\uDC31');
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void stringEquals() {
        check("hello world".equals("hello" + " " + "world"));
    }

    static void stringNotEqual() {
        check("hello world".equals("helloworld"));
//            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void stringIsEmpty(String str) {
        check(str.length() > 0 || str.isEmpty());
    }

    static void stringNotEmpty() {
        check("full".isEmpty());
//            ^^^^^^^^^^^^^^^^ Error: assertion does not hold
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

    static void catdog() {
        var cat = "🐱";
        var dog = "🐶";
        check(cat.length() == 2);
        check(dog.length() == 2);
        check(cat.charAt(0) == dog.charAt(0));
        check(cat.charAt(1) + 5 == dog.charAt(1));
        check((cat + dog).length() == 4);
    }

    static void testIndexOf() {
        var hello = "hello";

        char oChar = 'o';
        check(hello.charAt(4) == oChar);
        check(hello.indexOf('o')==4);   // Proven thanks to the check above
        check(hello.indexOf('e')==1);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
// The assertion above fails in CI but not on a local machine, instability probably due to the recursive function
// indexOf that may require too much verifier capacity
        check(hello.indexOf('3')==-1); // Proven without any help
    }

    static void testSubstring() {
        var hello = "hello";
        var he = "he";
        var hellohello = "hellohello";
        var hi = "f";
        check(hello.startsWith(he));
        check(!hello.startsWith(hellohello));
        check(hi.charAt(0) != hello.charAt(0));
        check(!hello.startsWith(hi));
    }

    static void testSubstring2(String s1, String s2) {
        precondition(s1.startsWith(s2));
        precondition(s2.length() > 3);
        String s3 = s2.substring(0,1);
        check(s1.startsWith(s3));
        String s4 = s2.substring(1);
        check(s2.startsWith(s4));
//            ^^^^^^^^^^^^^^^^^ Error: assertion does not hold

    }

    static void testConcat(String s1, String s2) {
        precondition(s1.length() == 3 && s2.length() == 5);
        precondition(!s2.startsWith(s1));
        String s3 = s1.concat(s2);
        check(s3.length() == 8);
        check(s3.startsWith(s1));
        check(s3.startsWith(s2));
//            ^^^^^^^^^^^^^^^^^ Error: assertion does not hold

    }
    
    void castStringToCharSequence() {
        var instant = java.time.Instant.parse("hello");
//                    ^ warning: missing contract for method 'parse' in class 'java.time.Instant'
    }
}
