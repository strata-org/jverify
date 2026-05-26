package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Contract;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Regression test for SwitchDesugarer's refusal-time diagnostics on switch
 * forms that can't be encoded as if/conditional chains. Each refused
 * construct should produce a notSupported diagnostic at its source position
 * rather than a generic JavaViolationException → method-skip.
 *
 * Methods are non-static deliberately. JavaToLaurelCompiler.translateStaticMethod
 * only runs on static methods, so e.g. casePattern's `Object` parameter
 * doesn't trigger a separate `Unsupported type` diagnostic that would clutter
 * the diagnostic-list comparison. Refusal diagnostics fire at the desugaring
 * phase, before any per-method translation runs.
 */
@JVerifyTest(exitCode = 2)
class SwitchRefusal {
    int statementGroup(int i) {
        return switch (i) {
            case 0:
//          ^ error: switch labeled statement group is not supported
                yield 0;
            case 1, 2:
//          ^ error: switch labeled statement group is not supported
                yield 1;
            default:
//          ^ error: switch labeled statement group is not supported
                yield -1;
        };
    }

    int casePattern(Object o) {
        return switch (o) {
            case Integer ii when ii < 0 -> -1;
//          ^ error: case pattern is not supported
            default -> 0;
        };
    }

    int blockBodyInExpression(int i) {
        return switch (i) {
            case 0 -> {
//                    ^ error: switch rule block is not supported
                yield 0;
            }
            default -> 1;
        };
    }

    int throwBodyInExpression(int i) {
        return switch (i) {
            case 0 -> throw new RuntimeException();
//                    ^ error: switch rule throw statement is not supported
            default -> 1;
        };
    }

    // RuntimeException needs a contract; without it MissingContractCompiler
    // emits a warning that adds noise to the diagnostic-list comparison.
    @Contract(RuntimeException.class)
    static class RuntimeExceptionContract {
        public RuntimeExceptionContract() {
        }
    }
}
