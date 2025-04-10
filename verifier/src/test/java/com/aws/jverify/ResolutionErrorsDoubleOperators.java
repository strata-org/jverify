package com.aws.jverify;

@SuppressWarnings("ConstantValue")
class ResolutionErrorsDoubleOperators {
    public void foo() {
        var l = 3.0;
        var r = 3.0;
        
        var equals = l == r;
//                     ^ error: operator ==(double,double) is not supported
        var notEquals = l != r;
//                        ^ error: operator !=(double,double) is not supported
        
        l++;
//       ^ error: operator ++(double) is not supported
        //check(l == 4f);
        l--;
//       ^ error: operator --(double) is not supported
        //check(l == 3f);
        ++l;
//      ^ error: operator ++(double) is not supported
        //check(l == 4f);
        --l;
//      ^ error: operator --(double) is not supported
        //check(l == 3f);
        
        var multiplication = l * r;
//                             ^ error: operator *(double,double) is not supported
        //check(multiplication == 9f);
        var division = l / r;
//                       ^ error: operator /(double,double) is not supported
        //check(division == 1f);
        var remainder = l % 2d;
//                        ^ error: operator %(double,double) is not supported
        //check(remainder == 1f);
        
        var addition = l + r;
//                       ^ error: operator +(double,double) is not supported
        //check(addition == 6f);
        var subtraction = l - r;
//                          ^ error: operator -(double,double) is not supported
        //check(subtraction == 0);
        
        var lessThan = l < r;
//                       ^ error: operator <(double,double) is not supported
        //check(!lessThan);
        var greaterThan = l > r;
//                          ^ error: operator >(double,double) is not supported
        //check(!greaterThan);
        var lessThanOrEquals = l <= r;
//                               ^ error: operator <=(double,double) is not supported
        //check(lessThanOrEquals);
        var greaterThanOrEquals = l >= r;
//                                  ^ error: operator >=(double,double) is not supported
        //check(greaterThanOrEquals);
        
        l += r;
//        ^ error: operator +(double,double) is not supported
        //check(l == 6f);
        l -= r;
//        ^ error: operator -(double,double) is not supported
        //check(l == 3f);
        l *= r;
//        ^ error: operator *(double,double) is not supported
        //check(l == 9f);
        l /= r;
//        ^ error: operator /(double,double) is not supported
        //check(l == 3f);
        l %= 2L;
//        ^ error: operator %(double,double) is not supported
        //check(l == 1f);
    } 
}
