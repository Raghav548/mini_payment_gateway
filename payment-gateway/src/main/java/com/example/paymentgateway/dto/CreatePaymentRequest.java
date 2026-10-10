package com.example.paymentgateway.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Currency;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotBlank
    @Size(max = 64)
    private String merchantId;

    @NotNull
    @DecimalMin(value = "0.01", message = "must be at least 0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3 letter uppercase code, eg. EUR")
    private String currency;

    @NotBlank
    @Size(max = 100)
    private String reference;


    @AssertTrue(message = "currency must be a valid ISO 4217 code")
    public boolean isCurrencyValid() {
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            return true;
        }

        try {
            Currency.getInstance(currency);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
