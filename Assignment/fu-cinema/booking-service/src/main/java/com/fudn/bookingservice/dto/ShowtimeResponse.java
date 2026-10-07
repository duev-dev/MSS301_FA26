package com.fudn.bookingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeResponse(String showtimeId, String movieId, String movieTitle,
                               String roomId, String roomName, int seatRows, int seatsPerRow,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal ticketPrice, String showtimeStatus) {
}
