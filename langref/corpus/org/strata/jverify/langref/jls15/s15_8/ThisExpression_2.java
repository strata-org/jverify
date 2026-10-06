package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.3 -- this: the keyword this denotes the current object in an instance method/constructor
class ThisExpression_2 {
    int value;

    ThisExpression_2(int value) {
        // 'this.value' disambiguates the field from the parameter
        this.value = value;
    }

    int doubled() {
        // 'this' refers to the current object
        return this.value + this.value;
    }

    static void run() {

        ThisExpression_2 obj = new ThisExpression_2(21);
        check(obj.doubled() == 42);
    }
}
