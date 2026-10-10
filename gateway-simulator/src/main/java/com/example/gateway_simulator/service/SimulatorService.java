package com.example.gateway_simulator.service;

import com.example.gateway_simulator.dto.WebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public SimulatorService(RestClient restClient, @Value("${gateway.webhook-url}") String webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    @Async
    public void process(UUID paymentId) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(2000, 5001));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        String result = ThreadLocalRandom.current().nextBoolean() ? "SUCCESS" : "FAILED";

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebhookPayload(paymentId, result))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Reported {} for payment {}", result, paymentId);
        } catch (RestClientException e) {
            log.error("Could not deliver webhook for payment {}", paymentId, e);
        }
    }
}