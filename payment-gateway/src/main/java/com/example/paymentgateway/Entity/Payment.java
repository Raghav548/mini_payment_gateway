package com.example.paymentgateway.Entity;

import com.example.paymentgateway.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_merchant_reference",
                columnNames = {"merchant_id", "reference"}))
@Getter
public class Payment {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() { }

    public Payment(String merchantId, BigDecimal amount, String currency, String reference) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = PaymentStatus.PENDING;
    }

    /** Moves to the next state if legal, and returns the previous state  */
    public PaymentStatus transitionTo(PaymentStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidStateTransitionException(status, next);
        }
        PaymentStatus from = this.status;
        this.status = next;
        return from;
    }



}
