package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nullable;
import com.aws.jverify.Union;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
@SuppressWarnings("unused")
class UnionsErrors {
    @Union
//  ^ error: @Union may only be applied to a sealed interface
    interface UnsealedInterface {}

    @Union
//  ^ error: @Union may only be applied to a sealed interface
    sealed static class SealedClass {
        static final class SealedClassRecord extends SealedClass {}
    }

    @Union
//  ^ error: @Union interfaces may only be implemented by records
    sealed interface ClassUnion {
        final class ClassVariant implements ClassUnion {}
    }

    @Union
    sealed interface ConsList {
        int sum();

        record Nil() implements ConsList {
            public int sum() { return 0; }
        }

        record Cons(int head, ConsList tail) implements ConsList {
            public int sum() { return head + tail.sum(); }
        }
    }

    void NullableUnion() {
        ConsList nonNullableList = new ConsList.Cons(1, new ConsList.Nil());
        @Nullable ConsList nullableList = null;
//      ^ error: nullable @Union type is not supported
    }
}
