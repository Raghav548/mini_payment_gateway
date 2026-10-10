package com.example.paymentgateway.service;

import com.example.paymentgateway.Entity.EventSource;
import com.example.paymentgateway.Entity.Payment;
import com.example.paymentgateway.Entity.PaymentEvent;
import com.example.paymentgateway.Entity.PaymentStatus;
import com.example.paymentgateway.dto.CreatePaymentRequest;
import com.example.paymentgateway.dto.PaymentCreationResult;
import com.example.paymentgateway.exception.IdempotencyConflictException;
import com.example.paymentgateway.exception.InvalidStateTransitionException;
import com.example.paymentgateway.gateway.GatewayClient;
import com.example.paymentgateway.repository.PaymentEventRepository;
import com.example.paymentgateway.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock PaymentEventRepository events;
    @Mock GatewayClient gatewayClient;
    @Mock PlatformTransactionManager txManager;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, events, new TransactionTemplate(txManager), gatewayClient);
    }

    private CreatePaymentRequest request(String amount) {
        return new CreatePaymentRequest("m1", new BigDecimal(amount), "EUR", "ref-1");
    }

    private Payment existingPayment() {
        return new Payment("m1", new BigDecimal("100.00"), "EUR", "ref-1");
    }

    private PaymentEvent savedEvent() {
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }

    // ---------- create / idempotency ----------

    @Test
    void create_newPayment_isPending_writesCreationEvent_andSubmitsToGateway() {
        when(payments.findByMerchantIdAndReference("m1", "ref-1")).thenReturn(Optional.empty());

        PaymentCreationResult result = service.createPayment(request("100.00"));

        assertTrue(result.isCreated());
        assertEquals(PaymentStatus.PENDING, result.getPayment().getStatus());
        PaymentEvent event = savedEvent();
        assertNull(event.getFromStatus());
        assertEquals(PaymentStatus.PENDING, event.getToStatus());
        assertEquals(EventSource.API, event.getSource());
        verify(gatewayClient).submit(result.getPayment().getId());
    }

    @Test
    void create_sameKeyAndSameDetails_returnsExisting_withoutInsertingOrCallingGateway() {
        Payment existing = existingPayment();
        when(payments.findByMerchantIdAndReference("m1", "ref-1")).thenReturn(Optional.of(existing));

        PaymentCreationResult result = service.createPayment(request("100.0"));

        assertFalse(result.isCreated());
        assertSame(existing, result.getPayment());
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(events, gatewayClient);
    }

    @Test
    void create_sameKeyButDifferentAmount_isRejected() {
        when(payments.findByMerchantIdAndReference("m1", "ref-1")).thenReturn(Optional.of(existingPayment()));

        assertThrows(IdempotencyConflictException.class, () -> service.createPayment(request("200.00")));

        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void create_losingTheRace_returnsTheWinner_andDoesNotCallGateway() {
        Payment winner = existingPayment();
        when(payments.findByMerchantIdAndReference("m1", "ref-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(payments.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentCreationResult result = service.createPayment(request("100.00"));

        assertFalse(result.isCreated());
        assertSame(winner, result.getPayment());
        verifyNoInteractions(events, gatewayClient);
    }


    @Test
    void capture_successfulPayment_becomesCaptured_andWritesEvent() {
        Payment p = existingPayment();
        p.transitionTo(PaymentStatus.SUCCESS);
        when(payments.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        Payment result = service.capture(p.getId());

        assertEquals(PaymentStatus.CAPTURED, result.getStatus());
        PaymentEvent event = savedEvent();
        assertEquals(PaymentStatus.SUCCESS, event.getFromStatus());
        assertEquals(PaymentStatus.CAPTURED, event.getToStatus());
        assertEquals(EventSource.API, event.getSource());
    }

    @Test
    void capture_pendingPayment_isRejected_andWritesNoEvent() {
        Payment p = existingPayment();   // still PENDING
        when(payments.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        assertThrows(InvalidStateTransitionException.class, () -> service.capture(p.getId()));

        assertEquals(PaymentStatus.PENDING, p.getStatus());
        verify(events, never()).save(any());
    }


    @Test
    void webhook_resolvesPendingPayment_andWritesWebhookEvent() {
        Payment p = existingPayment();
        when(payments.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        service.applyGatewayResult(p.getId(), PaymentStatus.SUCCESS);

        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        PaymentEvent event = savedEvent();
        assertEquals(PaymentStatus.PENDING, event.getFromStatus());
        assertEquals(PaymentStatus.SUCCESS, event.getToStatus());
        assertEquals(EventSource.WEBHOOK, event.getSource());
    }

    @Test
    void webhook_forPaymentThatIsNoLongerPending_isIgnored() {
        Payment p = existingPayment();
        p.transitionTo(PaymentStatus.SUCCESS);
        when(payments.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        service.applyGatewayResult(p.getId(), PaymentStatus.FAILED);   // late or duplicate update

        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        verify(events, never()).save(any());
    }
}