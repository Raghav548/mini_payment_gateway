package com.example.paymentgateway.exception;

import com.example.paymentgateway.Entity.PaymentStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot move payment mapToPaymentResponse " + from + " to " + to);
    }
}
