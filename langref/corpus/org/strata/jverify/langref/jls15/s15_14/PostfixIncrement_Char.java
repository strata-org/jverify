package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a char: advances the char by
// one code point; yields the pre-increment char value.
class PostfixIncrement_Char {
    static void run() {

        char x = 'a';
        char y = x++;
        check(x == 'b' && y == 'a');
    }
}
