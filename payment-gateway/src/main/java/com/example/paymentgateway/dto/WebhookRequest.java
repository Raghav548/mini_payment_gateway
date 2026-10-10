package com.example.paymentgateway.dto;

import com.example.paymentgateway.Entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequest {

    @NotNull
    private UUID paymentId;

    @NotNull
    private PaymentStatus status;

}
