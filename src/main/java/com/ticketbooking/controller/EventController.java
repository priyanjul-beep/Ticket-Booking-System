package com.ticketbooking.controller;

import com.ticketbooking.dto.CreateEventRequest;
import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.dto.SeatDTO;
import com.ticketbooking.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Event & Seat Management", description = "Event creation and seat inventory APIs")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "Create an event and generate seat inventory")
    public ResponseEntity<EventDTO> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventDTO event = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping
    @Operation(summary = "List all events")
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping("/{eventId}/seats")
    @Operation(summary = "Get all seats for an event")
    public ResponseEntity<List<SeatDTO>> getEventSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEventSeats(eventId));
    }

    @GetMapping("/{eventId}/seats/available")
    @Operation(summary = "Get all available seats for an event")
    public ResponseEntity<List<SeatDTO>> getAvailableSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getAvailableEventSeats(eventId));
    }
}
