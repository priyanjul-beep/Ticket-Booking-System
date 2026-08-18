package com.ticketbooking.controller;

import com.ticketbooking.concurrency.RaceConditionDemo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "Concurrency Demonstration", description = "Interactive endpoints demonstrating race conditions and locking strategies")
public class DemoController {

    private final RaceConditionDemo raceConditionDemo;

    @PostMapping("/race-condition/unsafe")
    @Operation(summary = "Demonstrate UNSAFE race condition (causes inventory corruption / overselling)")
    public ResponseEntity<RaceConditionDemo.DemoResult> runUnsafeDemo(
            @RequestParam(defaultValue = "10") int initialSeats,
            @RequestParam(defaultValue = "100") int concurrentRequests) throws InterruptedException {
        return ResponseEntity.ok(raceConditionDemo.runUnsafeDemo(initialSeats, concurrentRequests));
    }

    @PostMapping("/race-condition/safe")
    @Operation(summary = "Demonstrate SAFE concurrency control (prevents race condition & overselling)")
    public ResponseEntity<RaceConditionDemo.DemoResult> runSafeDemo(
            @RequestParam(defaultValue = "10") int initialSeats,
            @RequestParam(defaultValue = "100") int concurrentRequests) throws InterruptedException {
        return ResponseEntity.ok(raceConditionDemo.runSafeDemo(initialSeats, concurrentRequests));
    }
}
