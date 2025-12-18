package com.aws.jverify.builtin;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

class HelperForBigIntegerContract {
    @Erased
    @Pure
    static boolean isAllDigits(String v) {
        return forall((int i) -> !(0<=i && i<v.length()) || (v.charAt(i) >= '0' && v.charAt(i) <= '9'));
    }

    @Erased
    @Pure
    static boolean isValidString(String v) {
        return (v.length() == 0 ||
                ((v.charAt(0) == '+' || v.charAt(0) == '-') && isAllDigits(v.substring(1))) ||
                isAllDigits(v));
    }
    
    @Erased
    @Pure
    static @Unbounded int stringToInt(String v) {
        decreases(v.length());
        if (v.length() == 0) {
            return 0;
        }

        if (v.charAt(0) == '+') {
            return stringToInt(v.substring(1));
        }

        if (v.charAt(0) == '-') {
            return -stringToInt(v.substring(1));
        }

        return v.charAt(v.length()-1)-'0' + 
                10*stringToInt(v.substring(0,v.length()-1));
    }

    @Erased
    @Pure
    static @Unbounded int pow(@Unbounded int x, @Nat int n) {
        decreases(n);
        return (n == 0 ? 1 : x * pow(x, n-1));
    }
}