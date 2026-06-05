package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * Regression test for arrow-form switches reaching JavaToLaurelCompiler as
 * if/conditional chains. Switches.java covers more forms but stays skipped
 * pending case-null support.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 14, errorCount = 1)
class SwitchDesugaring {
    static void switchExpr(int i) {
        int num = switch (i) {
            case 0 -> 10;
            case 1, 2 -> 20;
            case 3 -> 30;
            default -> 40;
        };
        check(num == 10 || num == 20 || num == 30 || num == 40);
    }

    static void switchStmt(int i) {
        int num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> num = 20;
            case 3 -> num = 30;
            default -> num = 40;
        }
        check(num == 10 || num == 20 || num == 30 || num == 40);
    }

    static void switchStmtNonExhaustive(int i) {
        int num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> num = 20;
            case 3 -> num = 30;
        }
        if (i == 0) check(num == 10);
        else if (i == 1 || i == 2) check(num == 20);
        else if (i == 3) check(num == 30);
        else check(num == -1);
    }

    static void switchStmtBlockBody(int i) {
        int num = -1;
        switch (i) {
            case 0 -> num = 10;
            case 1, 2 -> {
                num = 20;
                num = num + 5;
            }
            default -> num = 40;
        }
        check(num == 10 || num == 25 || num == 40);
    }

    static void switchExprDefaultLast(int i) {
        int num = switch (i) {
            case 0 -> 10;
            default -> 40;
        };
        check(num == 10 || num == 40);
    }

    static void switchExprDefaultFirst(int i) {
        int num = switch (i) {
            default -> 99;
            case 0 -> 10;
            case 1 -> 20;
        };
        check(num == 10 || num == 20 || num == 99);
    }

    static void switchExprChar(char c) {
        int group = switch (c) {
            case 'a' -> 1;
            case 'b', 'c' -> 2;
            default -> 3;
        };
        check(1 <= group && group <= 3);
    }

    static void switchExprBad(int i) {
        int num = switch (i) {
            case 0 -> 10;
            default -> 40;
        };
        check(num == 99);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void switchStmtDefaultNotLast(int i) {
        // Statement-form analogue of switchExprDefaultFirst. The if-cascade
        // would mis-encode if `default` lowered to `else if (true)` —
        // case 1's branch would be unreachable and verification would prove
        // `i == 1 ⇒ r == 99`. Asserting r == 20 catches the bug.
        int r = -1;
        switch (i) {
            case 0 -> r = 10;
            default -> r = 99;
            case 1 -> r = 20;
        }
        if (i == 0) {
            check(r == 10);
        } else if (i == 1) {
            check(r == 20);
        } else {
            check(r == 99);
        }
    }

    // Non-trivial selectors exercise the selector-hoisting machinery:
    // the cascade tests $sel against each case constant, so the original
    // selector expression should be evaluated once via the synthetic
    // varDecl / LetExpr.
    static void switchStmtNonTrivialSelector(int i) {
        precondition(0 <= i && i < 100);
        int r = -1;
        switch (i + 1) {
            case 1 -> r = 10;
            case 2 -> r = 20;
            default -> r = 99;
        }
        check(r == 10 || r == 20 || r == 99);
    }

    static void switchExprNonTrivialSelector(int i) {
        precondition(0 <= i && i < 100);
        int num = switch (i + 1) {
            case 1 -> 10;
            case 2 -> 20;
            default -> 99;
        };
        check(num == 10 || num == 20 || num == 99);
    }

    // Regression test: switch expression inside a postcondition lambda whose
    // selector references the lambda param. Post-SwitchDesugarer the switch
    // becomes a LetExpr whose VarDef initializer references the lambda param —
    // that initializer must go through the renames-aware path or Strata sees
    // an unbound identifier.
    static int switchInPostcondition(int r) {
        postcondition((int x) -> 0 == switch (x) {
            case 0 -> 0;
            case 1 -> 0;
            default -> 0;
        });
        return 0;
    }

    // Compile-time constant case labels. javac keeps these as JCFieldAccess
    // (qualified to the declaring class); convertExpression's constValue arm
    // folds them to literals at translation time.
    static final int CONST_ONE = 1;
    static class Constants { static final int OUTSIDE = 7; }
    static void switchExprCaseConst(int i) {
        int num = switch (i) {
            case CONST_ONE -> 10;
            case Constants.OUTSIDE -> 20;
            default -> 99;
        };
        if (i == CONST_ONE) check(num == 10);
        else if (i == Constants.OUTSIDE) check(num == 20);
        else check(num == 99);
    }
}
