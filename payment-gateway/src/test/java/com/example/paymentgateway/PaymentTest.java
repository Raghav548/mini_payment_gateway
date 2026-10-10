package com.example.paymentgateway;

import com.example.paymentgateway.Entity.Payment;
import com.example.paymentgateway.Entity.PaymentStatus;
import com.example.paymentgateway.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static com.example.paymentgateway.Entity.PaymentStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private Payment newPayment() {
        return new Payment("m1", new BigDecimal("100.00"), "EUR", "ref-1");
    }

    /** Walks a fresh payment to the wanted state through legal moves only. */
    private Payment paymentIn(PaymentStatus target) {
        Payment p = newPayment();
        switch (target) {
            case PENDING -> { }
            case SUCCESS -> p.transitionTo(SUCCESS);
            case FAILED -> p.transitionTo(FAILED);
            case CAPTURED -> { p.transitionTo(SUCCESS); p.transitionTo(CAPTURED); }
            case REFUNDED -> { p.transitionTo(SUCCESS); p.transitionTo(CAPTURED); p.transitionTo(REFUNDED); }
        }
        return p;
    }

    @Test
    void happyPathMovesThroughEveryStateAndReportsThePreviousOne() {
        Payment p = newPayment();
        assertEquals(PENDING, p.transitionTo(SUCCESS));
        assertEquals(SUCCESS, p.transitionTo(CAPTURED));
        assertEquals(CAPTURED, p.transitionTo(REFUNDED));
        assertEquals(REFUNDED, p.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "FAILED", "CAPTURED", "REFUNDED"})
    void onlySuccessfulPaymentsCanBeCaptured(PaymentStatus from) {
        Payment p = paymentIn(from);
        assertThrows(InvalidStateTransitionException.class, () -> p.transitionTo(CAPTURED));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "SUCCESS", "FAILED", "REFUNDED"})
    void onlyCapturedPaymentsCanBeRefunded_andOnlyOnce(PaymentStatus from) {
        Payment p = paymentIn(from);   // REFUNDED here = the "refund twice" case
        assertThrows(InvalidStateTransitionException.class, () -> p.transitionTo(REFUNDED));
    }
}