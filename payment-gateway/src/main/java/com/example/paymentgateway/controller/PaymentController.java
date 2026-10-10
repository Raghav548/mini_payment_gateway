package com.example.paymentgateway.controller;

import com.example.paymentgateway.dto.CreatePaymentRequest;
import com.example.paymentgateway.dto.PaymentResponse;
import com.example.paymentgateway.dto.PaymentCreationResult;
import com.example.paymentgateway.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;



    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentCreationResult result = paymentService.createPayment(request);
        PaymentResponse body = PaymentResponse.mapToPaymentResponse(result.getPayment());
        if (result.isCreated()) {
            return ResponseEntity.created(URI.create("/payments/" + body.getId())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.mapToPaymentResponse(paymentService.get(id));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable UUID id) {
        return PaymentResponse.mapToPaymentResponse(paymentService.capture(id));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) {
        return PaymentResponse.mapToPaymentResponse(paymentService.refund(id));
    }
}
