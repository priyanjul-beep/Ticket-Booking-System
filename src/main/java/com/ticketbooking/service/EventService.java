package com.ticketbooking.service;

import com.ticketbooking.dto.CreateEventRequest;
import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.dto.SeatDTO;
import com.ticketbooking.entity.Event;
import com.ticketbooking.entity.Seat;
import com.ticketbooking.entity.SeatStatus;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public EventDTO createEvent(CreateEventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .description(request.getDescription())
                .venue(request.getVenue())
                .eventDate(request.getEventDate())
                .totalSeats(request.getTotalSeats())
                .availableSeats(request.getTotalSeats())
                .build();

        Event savedEvent = eventRepository.save(event);

        // Generate seats A1, A2, ... A10, B1, B2...
        List<Seat> seats = new ArrayList<>();
        int seatsPerRow = 10;
        for (int i = 0; i < request.getTotalSeats(); i++) {
            char rowChar = (char) ('A' + (i / seatsPerRow));
            int seatNum = (i % seatsPerRow) + 1;
            String seatNumberStr = "" + rowChar + seatNum;

            Seat seat = Seat.builder()
                    .event(savedEvent)
                    .seatNumber(seatNumberStr)
                    .category("REGULAR")
                    .price(request.getDefaultPrice())
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build();
            seats.add(seat);
        }
        seatRepository.saveAll(seats);

        return mapToDTO(savedEvent);
    }

    @Transactional(readOnly = true)
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream().map(this::mapToDTO).toList();
    }

    @Transactional(readOnly = true)
    public EventDTO getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));
        return mapToDTO(event);
    }

    @Transactional(readOnly = true)
    public List<SeatDTO> getEventSeats(Long eventId) {
        return seatRepository.findByEventId(eventId).stream().map(this::mapSeatToDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatDTO> getAvailableEventSeats(Long eventId) {
        return seatRepository.findByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)
                .stream().map(this::mapSeatToDTO).toList();
    }

    public EventDTO mapToDTO(Event event) {
        return EventDTO.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .totalSeats(event.getTotalSeats())
                .availableSeats(event.getAvailableSeats())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public SeatDTO mapSeatToDTO(Seat seat) {
        return SeatDTO.builder()
                .id(seat.getId())
                .eventId(seat.getEvent().getId())
                .seatNumber(seat.getSeatNumber())
                .category(seat.getCategory())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .version(seat.getVersion())
                .build();
    }
}
