package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class Operators {
    static void Plus() {
        int x = 3;
        int y = 4;
        int z = x + y;
        check(z==7);
    }

    static void Minus() {
        int x = 3;
        int y = 4;
        int z = y - x;
        check(z==1);
    }

    static void Mult() {
        int x = 3;
        int y = 4;
        int z = x * y;
        check(z==12);
    }

    static void Mod() {
        int x = 3;
        int y = 23;
        int z = y % x;
        check(z==2);
    }

    static void Gt() {
        int x = 3;
        int y = 23;
        check(y > x);
    }

    static void Lt() {
        int x = 3;
        int y = 23;
        check(x < y);
    }

    static void Leq() {
        int x = 3;
        int y = 23;
        check(x <= y);
    }

    static void Geq() {
        int x = 3;
        int y = 23;
        check(y >= x);
    }

    static void Eq() {
        int x = 3;
        int y = 3;
        check(x == y);
    }


}