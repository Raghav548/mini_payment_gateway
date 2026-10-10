package com.example.paymentgateway.dto;

import com.example.paymentgateway.Entity.Payment;

public class PaymentCreationResult {

    private final Payment payment;
    private final boolean created;

    public PaymentCreationResult(Payment payment, boolean created) {
        this.payment = payment;
        this.created = created;
    }

    public Payment getPayment() {
        return payment;
    }

    public boolean isCreated() {
        return created;
    }
}