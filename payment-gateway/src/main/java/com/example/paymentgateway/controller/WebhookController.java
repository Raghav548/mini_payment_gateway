package com.example.paymentgateway.controller;

import com.example.paymentgateway.dto.WebhookRequest;
import com.example.paymentgateway.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final PaymentService service;

    public WebhookController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@Valid @RequestBody WebhookRequest request) {
        service.applyGatewayResult(request.getPaymentId(), request.getStatus());
        return ResponseEntity.ok().build();
    }
}