package com.example.paymentgateway.dto;

import com.example.paymentgateway.Entity.Payment;
import com.example.paymentgateway.Entity.PaymentStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PaymentResponse {

    private UUID id;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private PaymentStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public PaymentResponse(UUID id, String merchantId, BigDecimal amount,
                           String currency, String reference, PaymentStatus status,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentResponse mapToPaymentResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getMerchantId(),
                p.getAmount(),
                p.getCurrency(),
                p.getReference(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
