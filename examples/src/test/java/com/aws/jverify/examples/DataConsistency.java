package com.aws.jverify.examples;

import com.aws.jverify.Invariant;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;

import java.math.BigDecimal;
import java.util.Date;

record Interval(Date startDate, Date endDate) {
    @Invariant @Pure
    public boolean isValid() {
        return startDate().compareTo(endDate()) < 0;
    }
}

record Percentage(BigDecimal percentage) {
    @Invariant @Pure
    public boolean isValid() {
        return BigDecimal.ZERO.compareTo(percentage) <= 0 &&
                percentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}

record ARN(String id)  {
    @Invariant @Pure
    public boolean isValid() {
        return id().startsWith("arn:aws:");
    }
}

record DocuSignId(String docuSignId) {
    @Invariant @Pure
    public boolean isValid() {
        return !this.docuSignId().isEmpty() && this.docuSignId().length() <= 1000;
    }
}