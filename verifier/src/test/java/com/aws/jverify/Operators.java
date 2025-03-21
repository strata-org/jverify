package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class Operators {
    static void Plus() {
        int x = 3;
        int y = 4;
        int z = x + y;
        check(z==7);
    }

    static void PlusKO() {
        int x = 3;
        int y = 4;
        int z = x + y;
        check(z==6);
    }

    static void Minus() {
        int x = 3;
        int y = 4;
        int z = y - x;
        check(z==1);
    }

    static void MinusKO() {
        int x = 3;
        int y = 4;
        int z = y - x;
        check(z==0);
    }

    static void Mult() {
        int x = 3;
        int y = 4;
        int z = x * y;
        check(z==12);
    }

    static void MultKO() {
        int x = 3;
        int y = 4;
        int z = x * y;
        check(z==11);
    }

    static void Mod() {
        int x = 3;
        int y = 23;
        int z = y % x;
        check(z==2);
    }

    static void ModKO() {
        int x = 3;
        int y = 23;
        int z = y % x;
        check(z==1);
    }

    static void Gt() {
        int x = 3;
        int y = 23;
        check(y > x);
    }

    static void GtKO() {
        int x = 33;
        int y = 23;
        check(y > x);
    }

    static void Lt() {
        int x = 3;
        int y = 23;
        check(x < y);
    }

    static void LtKO() {
        int x = 33;
        int y = 23;
        check(x < y);
    }

    static void Leq() {
        int x = 3;
        int y = 23;
        check(x <= y);
    }

    static void LeqKO() {
        int x = 33;
        int y = 23;
        check(x <= y);
    }

    static void Geq() {
        int x = 3;
        int y = 23;
        check(y >= x);
    }

    static void GeqKO() {
        int x = 33;
        int y = 23;
        check(y >= x);
    }

    static void Eq() {
        int x = 3;
        int y = 3;
        check(x == y);
    }

    static void EqKO() {
        int x = 4;
        int y = 3;
        check(x == y);
    }


}