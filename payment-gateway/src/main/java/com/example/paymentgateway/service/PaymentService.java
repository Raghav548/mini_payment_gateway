package com.example.paymentgateway.service;

import com.example.paymentgateway.Entity.EventSource;
import com.example.paymentgateway.Entity.Payment;
import com.example.paymentgateway.Entity.PaymentEvent;
import com.example.paymentgateway.Entity.PaymentStatus;
import com.example.paymentgateway.dto.CreatePaymentRequest;
import com.example.paymentgateway.dto.PaymentCreationResult;
import com.example.paymentgateway.exception.IdempotencyConflictException;
import com.example.paymentgateway.exception.PaymentNotFoundException;
import com.example.paymentgateway.gateway.GatewayClient;
import com.example.paymentgateway.repository.PaymentEventRepository;
import com.example.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final GatewayClient gatewayClient;

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);


    public PaymentCreationResult createPayment(CreatePaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByMerchantIdAndReference(request.getMerchantId(), request.getReference());
        if (existing.isPresent()) {
            return idemmpotencyCheck(existing.get(), request);
        }
        try {
            Payment saved = transactionTemplate.execute(status -> insert(request));
            gatewayClient.submit(saved.getId());
            return new PaymentCreationResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            Payment winner = paymentRepository.findByMerchantIdAndReference(request.getMerchantId(), request.getReference())
                    .orElseThrow(() -> e);
            return idemmpotencyCheck(winner, request);
        }
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional
    public Payment capture(UUID id) {
        Payment payment = lockById(id);
        transition(payment, PaymentStatus.CAPTURED, EventSource.API);
        return payment;
    }

    @Transactional
    public Payment refund(UUID id) {
        Payment payment = lockById(id);
        transition(payment, PaymentStatus.REFUNDED, EventSource.API);
        return payment;
    }

    @Transactional
    public void applyGatewayResult(UUID id, PaymentStatus result) {
        Payment payment = lockById(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Ignoring webhook for payment {}: already {}, update said {}", id, payment.getStatus(), result);
            return;
        }
        transition(payment, result, EventSource.WEBHOOK);
    }

    private Payment lockById(UUID id) {
        return paymentRepository.findByIdForUpdate(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private void transition(Payment payment, PaymentStatus next, EventSource source) {
        PaymentStatus from = payment.transitionTo(next);
        paymentEventRepository.save(new PaymentEvent(payment.getId(), from, next, source));
    }

    private Payment insert(CreatePaymentRequest req) {
        Payment payment = new Payment(req.getMerchantId(), req.getAmount(), req.getCurrency(), req.getReference());
        paymentRepository.saveAndFlush(payment);
        paymentEventRepository.save(new PaymentEvent(payment.getId(), null, PaymentStatus.PENDING, EventSource.API));
        return payment;
    }

    private PaymentCreationResult idemmpotencyCheck(Payment existing, CreatePaymentRequest req) {
        boolean sameDetails = existing.getAmount().compareTo(req.getAmount()) == 0
                && existing.getCurrency().equals(req.getCurrency());
        if (!sameDetails) {
            throw new IdempotencyConflictException(req.getMerchantId(), req.getReference());
        }
        return new PaymentCreationResult(existing, false);
    }
}
