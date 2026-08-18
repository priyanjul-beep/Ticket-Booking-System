package com.ticketbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    private Long id;
    private String name;
    private String description;
    private String venue;
    private ZonedDateTime eventDate;
    private Integer totalSeats;
    private Integer availableSeats;
    private ZonedDateTime createdAt;
}
