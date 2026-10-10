package com.example.paymentgateway.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final RestClient restClient;
    private final String simulatorUrl;

    public GatewayClient(RestClient restClient, @Value("${gateway.simulator-url}") String simulatorUrl) {
        this.restClient = restClient;
        this.simulatorUrl = simulatorUrl;
    }

    @Async
    public void submit(UUID paymentId) {
        int maxAttempts = 3;
        long delayMs = 500;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri(simulatorUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("paymentId", paymentId))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Submitted payment {} to gateway", paymentId);
                return;
            } catch (RestClientException e) {
                log.warn("Gateway submit failed for payment {} (attempt {}/{})", paymentId, attempt, maxAttempts, e);
                if (attempt == maxAttempts) {
                    log.error("Giving up on payment {}; it stays PENDING", paymentId);
                    return;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delayMs *= 2;
            }
        }
    }
}