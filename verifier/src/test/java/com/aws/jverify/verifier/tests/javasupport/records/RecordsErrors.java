package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
@SuppressWarnings({"RedundantRecordConstructor", "unused"})
class RecordsErrors {
    record ExplicitConstructors(int i, boolean b) {
        // Explicit canonical constructors are forbidden
        ExplicitConstructors(int i, boolean b) {
//      ^ error: verified explicit record constructor is not supported
            this.i = i;
            this.b = b;
        }

        // Other explicit constructors are also forbidden
        ExplicitConstructors() {
//      ^ error: verified explicit record constructor is not supported
            this(1, true);
        }
    }

    record ExplicitAccessors(int i, boolean b) {
        public int i() {
//                 ^ error: explicit record component accessor method is not supported
            return i;
        }

        // A method with a component's name, but with a non-empty parameter list, is fine
        public int i(int j) {
            return i;
        }

        public boolean b() {
//                     ^ error: explicit record component accessor method is not supported
            return b;
        }
    }

    record ForbiddenOverrides() {
        public boolean equals(Object o) {
//                     ^ error: overridden equals method in record is not supported
            return false;
        }

        public int hashCode() {
//                 ^ error: overridden hashCode method in record is not supported
            return -1;
        }

        // A method may share its name with a forbidden-to-override method,
        // as long as it's not override-equivalent to any forbidden-to-override method.
        public int hashCode(int i) {
            return 4;  // chosen by fair dice roll.
                       // guaranteed to be random.
        }
    }

    record DoorStuck() implements IDoor {
//  ^ error: a record class may not be annotated with @Modifiable, or extend or implement a type annotated with @Modifiable
        @Override public boolean open() { return false; }
        @Override public boolean close() { return false; }
    }

    @Modifiable
    interface IDoor {
        boolean open();
        boolean close();
    }
    
    @Contract(value = WantsContract.class, immutable = true)
//  ^ error: class 'WantsContract' must not have an externally defined contract because all its contracts can be defined internally
    static class WantsContractContract {}
    
    static class WantsContract {}

    static boolean nullableString(@Nullable DoorStuck s) {
        return s == null;
    }
}
