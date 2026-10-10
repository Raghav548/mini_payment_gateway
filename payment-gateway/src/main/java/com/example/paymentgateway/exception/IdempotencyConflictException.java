package com.example.paymentgateway.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String merchantId, String reference) {
        super("A payment with merchantId '" + merchantId + "' and reference '" + reference
                + "' already exists with different details");
    }
}
