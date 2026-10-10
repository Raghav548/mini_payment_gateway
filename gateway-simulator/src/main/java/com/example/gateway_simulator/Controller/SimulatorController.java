package com.example.gateway_simulator.controller;

import com.example.gateway_simulator.dto.SimulationRequest;
import com.example.gateway_simulator.service.SimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway")
public class SimulatorController {

    private final SimulatorService service;

    public SimulatorController(SimulatorService service) {
        this.service = service;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> submit(@RequestBody SimulationRequest request) {
        if (request.getPaymentId() == null) {
            return ResponseEntity.badRequest().build();
        }
        service.process(request.getPaymentId());
        return ResponseEntity.accepted().build();
    }
}