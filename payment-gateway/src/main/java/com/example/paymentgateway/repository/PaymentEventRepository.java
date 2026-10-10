package com.example.paymentgateway.repository;

import com.example.paymentgateway.Entity.PaymentEvent;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;


public interface PaymentEventRepository extends Repository<PaymentEvent, Long> {

    PaymentEvent save(PaymentEvent event);

    List<PaymentEvent> findByPaymentIdOrderByIdAsc(UUID paymentId);
}
