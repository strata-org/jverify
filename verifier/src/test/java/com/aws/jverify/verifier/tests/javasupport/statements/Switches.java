package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
@JVerifyTest(exitCode = 4, javaVerified = 7, javaErrors = 4)
class Switches {
    static void switchExprInt(int i) {
        var num = switch (i) {
            case 0 -> 10;
            case 1, 2 -> 20;
            case 3 -> 30;
            default -> 40;
        };
        check(num % 10 == 0);
    }

    static void switchExprIntBad(int i) {
        var num = switch (i) {
            case 0 -> 10;
            case 1, 2 -> 20;
            case 3 -> 30;
            default -> 40;
        };
        check(num % 20 == 0);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void switchStmtInt(int i) {
        var num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> num = 20;
            case 3 -> num = 30;
            default -> num = 40;
        }
        check(num % 10 == 0);
    }

    static void switchStmtIntBad(int i) {
        var num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> num = 20;
            case 3 -> num = 30;
            default -> num = 40;
        }
        check(num % 20 == 0);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void switchExprChar(char c) {
        var group = switch (c) {
            case 'a', 'e', 'i', 'o', 'u' -> 1;
            case 'x', 'y', 'z' -> 2;
            case ' ' -> 3;
            default -> 4;
        };
        check(1 <= group && group <= 4);
    }

    static void switchExprCharBad(char c) {
        var group = switch (c) {
            case 'a', 'e', 'i', 'o', 'u' -> 1;
            case 'x', 'y', 'z' -> 2;
            case ' ' -> 3;
            default -> 4;
        };
        check(1 <= group && group <= 3);
//                          ^^^^^^^^^^ Error: assertion could not be proved
    }

    static void switchExprObj(int @Nullable [] arr) {
        //noinspection SwitchStatementWithTooFewBranches
        var isNull = switch (arr) {
            case null -> true;
            default -> false;
        };
        check(isNull == (arr == null));
    }

    static void switchExprObjBad(int @Nullable [] arr) {
        //noinspection SwitchStatementWithTooFewBranches
        var isNull = switch (arr) {
            case null -> true;
            default -> false;
        };
        check(!isNull);
//      ^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void switchStmtBlockBody(int i) {
        var num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2, 3, 4 -> {
                num = 5;
                switch (i) {
                    case 1, 2 -> num = num + 25;
                    case 3, 4 -> {
                        num = num + 31;
                        num = num + 14;
                    }
                }
            }
            default -> num = 60;
        }
        check(num % 10 == 0);
    }

    static void switchStmtNonExhaustive(int i) {
        var num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> num = 20;
            case 3 -> num = 30;
        }
        check(num == -1 || num % 10 == 0);
    }
}
