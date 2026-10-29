package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.3 -- this: the keyword this denotes the current object in an instance method/constructor
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.ThisExpression_1#run")
class ThisExpression_1 {
    int value;

    ThisExpression_1(int value) {
        // 'this.value' disambiguates the field from the parameter
        this.value = value;
    }

    int doubled() {
        // 'this' refers to the current object
        return this.value + this.value;
    }

    static void run() {

        ThisExpression_1 obj = new ThisExpression_1(21);
        check(obj.value == 21);
    }
}
