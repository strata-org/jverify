package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A {@code case X -> throw ...} arm belongs to the switch IMMEDIATELY around it.
 *
 * <p>Here the throwing arm is in a switch STATEMENT, which is nested inside a switch
 * EXPRESSION's arm block. A throw there is an ordinary throw, not a value-producing arm,
 * so the "switch rule throw statement" error must NOT fire -- the outer expression is
 * already refused whole-method, and reporting the inner arm turned that warning into a
 * hard {@code exitCode = 2} at a position where nothing is wrong.
 *
 * <p>The earlier check asked only whether the scan was anywhere inside a switch
 * expression, which is true here, so it misfired. The discriminator has to be the kind of
 * the enclosing switch.
 *
 * <p>So the only diagnostics are the whole-method ones: the outer switch expression is
 * untranslated, and the throw needs a declared {@code throws}.
 */
@JVerifyTest(exitCode = 0, continueOnErrors = true)
class SwitchStatementInExpressionArm {

    static int sink;

    int test(int i) {
//      ^ warning: instance method 'test' was not verified: throw requires the enclosing method to declare the thrown type in its throws clause
        return switch (i) {
            case 0 -> {
                switch (i) {
                    case 0 -> throw new RuntimeException();
                    default -> {
                        sink++;
                    }
                }
                yield 1;
            }
            default -> 2;
        };
    }
}
