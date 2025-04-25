// TEST: exitCode=4 dafnyVerified=3 dafnyErrors=3

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nullable;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings({"ConstantValue", "unused"})
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
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
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
//                          ^^^^^^^^^^ Error: assertion might not hold
    }

    static void switchExprObj(@Nullable int[] arr) {
        //noinspection SwitchStatementWithTooFewBranches
        var isNull = switch (arr) {
            case null -> true;
            default -> false;
        };
        check(isNull == (arr == null));
    }

    static void switchExprObjBad(@Nullable int[] arr) {
        //noinspection SwitchStatementWithTooFewBranches
        var isNull = switch (arr) {
            case null -> true;
            default -> false;
        };
        check(!isNull);
//      ^^^^^^^^^^^^^^ Error: assertion might not hold
    }
}