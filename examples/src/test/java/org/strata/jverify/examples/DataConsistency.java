package org.strata.jverify.examples;

import org.strata.jverify.Invariant;
import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;

import java.math.BigDecimal;
import java.util.Date;

record Interval(Date startDate, Date endDate) {
    @Invariant @Transparent
    public boolean isValid() {
        return startDate().compareTo(endDate()) < 0;
    }
}

record Percentage(BigDecimal percentage) {
    @Invariant @Transparent
    public boolean isValid() {
        return BigDecimal.ZERO.compareTo(percentage) <= 0 &&
                percentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}