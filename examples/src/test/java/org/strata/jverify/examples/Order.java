package org.strata.jverify.examples;

import org.strata.jverify.Invariant;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;

import static org.strata.jverify.JVerify.modifies;
import static org.strata.jverify.JVerify.reads;

public class Order {
    public enum Status { PLACED, SHIPPED, COMPLETED }

    private Status status;
    private @Nullable String trackingNumber;

    @Pure
    @Invariant
    private boolean Valid() {
        reads(this);
        return (status == Status.SHIPPED) == (trackingNumber != null);
    }

    public Order() {
        this.status = Status.PLACED;
        this.trackingNumber = null;
    }
    
    public void display() {
        if (status == Status.PLACED) {
            System.out.println("Status: placed");
            System.out.println("Tracking number: " + trackingNumber);
//                                                   ^^^^^^^^^^^^^^ Error: destructor 'value' can only be applied to datatype values constructed by 'NonNull'
        } else if (status == Status.SHIPPED) {
            System.out.println("Status: shipped");
            System.out.println("Tracking number: " + trackingNumber);
        } else if (status == Status.COMPLETED) {
            System.out.println("Status: completed");
        }
    }

    public void ship(String trackingNumber) {
        modifies(this);
        this.status = Status.SHIPPED;
        this.trackingNumber = trackingNumber;
    }

    public void complete() {
        modifies(this);
        this.status = Status.COMPLETED;
        this.trackingNumber = null;
    }
}
