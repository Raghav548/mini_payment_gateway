package com.example.paymentgateway.Entity;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, CAPTURED, REFUNDED;

    public boolean canMoveTo(PaymentStatus next) {
        return switch (this) {
            case PENDING  -> next == SUCCESS || next == FAILED;
            case SUCCESS  -> next == CAPTURED;
            case CAPTURED -> next == REFUNDED;
            case FAILED, REFUNDED -> false;   // terminal states
        };
    }
}
