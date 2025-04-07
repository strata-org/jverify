package com.aws.jverify;

class Types {
    public void foo() {
        short x = 10;
        int y = 20;
        long z = 30;

        @Nat short a = -10;
        @Nat int b = -1 * y;
        @Nat long c = -1L * z;

//        x = (short)y;
//        y = (int)z;
//        z = (long)x;
    }
}
