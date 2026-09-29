
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Booking;
import com.bookmyroom.bookmyroom.repository.BookingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/stays")
public class CheckInOutController {

    private final BookingRepository bookingRepository;

    public CheckInOutController(
            BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // 1. CHECK IN GUEST
    @PutMapping("/{id}/check-in")
    public Booking checkIn(@PathVariable Long id) {

        Booking booking = findBooking(id);

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only confirmed bookings can be checked in"
            );
        }

        LocalDate today = LocalDate.now();

        if (today.isBefore(booking.getCheckIn())
                || !today.isBefore(booking.getCheckOut())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Check-in is allowed only during the booked stay"
            );
        }

        booking.setStatus("CHECKED_IN");
        booking.setActualCheckIn(LocalDateTime.now());

        return bookingRepository.save(booking);
    }

    // 2. CHECK OUT GUEST
    @PutMapping("/{id}/check-out")
    public Booking checkOut(@PathVariable Long id) {

        Booking booking = findBooking(id);

        if (!"CHECKED_IN".equals(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Guest must be checked in before checkout"
            );
        }

        booking.setStatus("CHECKED_OUT");
        booking.setActualCheckOut(LocalDateTime.now());

        return bookingRepository.save(booking);
    }

    // 3. VIEW CURRENTLY CHECKED-IN GUESTS
    @GetMapping("/active")
    public List<Booking> getActiveStays() {
        return bookingRepository.findByStatus("CHECKED_IN");
    }

    // 4. VIEW COMPLETED STAYS
    @GetMapping("/history")
    public List<Booking> getStayHistory() {
        return bookingRepository.findByStatus("CHECKED_OUT");
    }

    // 5. VIEW STAY DETAILS
    @GetMapping("/booking/{id}")
    public Booking getStayDetails(@PathVariable Long id) {
        return findBooking(id);
    }

    // FIND BOOKING BY ID
    private Booking findBooking(Long id) {

        return bookingRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Booking not found"
                        )
                );
    }
}