package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Initializing a class initializes its superclass and the superinterfaces that
 * declare a default method, but not an interface that declares only constants
 * and abstract methods. Initializing an interface initializes nothing else.
 *
 * <p>Each initializer below writes {@code log}. A method that reads
 * {@code log}, uses a class, and reads {@code log} again knows the two reads
 * agree only if the use runs no initializer that writes it. The last method
 * reads {@code Base.base} around a use of {@code Plain}: no initializer
 * that use runs writes {@code Base.base}, so the reads agree.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 2)
class InterfaceInitialization {

    static int log;

    interface Plain {
        int LIMIT = compute();

        static int compute() {
            log = 1;
            return 10;
        }

        void run();
    }

    interface WithDefault {
        int LIMIT = computeDefault();

        static int computeDefault() {
            log = 2;
            return 20;
        }

        default int twice() {
            return 2;
        }
    }

    static class Base {
        static int base = 3;

        static {
            log = 3;
        }
    }

    static class UsesPlain extends Base implements Plain {
        static int own;

        public void run() { }
    }

    static class UsesDefault implements WithDefault {
        static int own;
    }

    static void plainInterfaceIsNotInitialized() {
        log = 0;
        int before = log;
        int v = UsesPlain.own;
        check(log == before);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'Base', which this method may run
    }

    static void defaultInterfaceIsInitialized() {
        log = 0;
        int before = log;
        int v = UsesDefault.own;
        check(log == before);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'WithDefault', which this method may run
    }

    static void interfaceInitializesOnlyItself() {
        int a = Base.base;
        int v = Plain.LIMIT;
        int b = Base.base;
        check(a == b);
    }
}
