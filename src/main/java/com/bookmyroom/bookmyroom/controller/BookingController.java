
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Booking;
import com.bookmyroom.bookmyroom.model.Guest;
import com.bookmyroom.bookmyroom.model.Room;
import com.bookmyroom.bookmyroom.model.Maintenance;

import com.bookmyroom.bookmyroom.repository.BookingRepository;
import com.bookmyroom.bookmyroom.repository.GuestRepository;
import com.bookmyroom.bookmyroom.repository.RoomRepository;
import com.bookmyroom.bookmyroom.repository.MaintenanceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final MaintenanceRepository maintenanceRepository;

    public BookingController(
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            GuestRepository guestRepository,
            MaintenanceRepository maintenanceRepository) {

        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestBody Booking request) {

        if (request.getRoom() == null
                || request.getRoom().getId() == null
                || request.getGuest() == null
                || request.getGuest().getId() == null
                || request.getCheckIn() == null
                || request.getCheckOut() == null) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Room, guest and booking dates are required.")
            );
        }

        if (!request.getCheckIn()
                .isBefore(request.getCheckOut())) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Checkout must be after check-in.")
            );
        }

        Room room = roomRepository
                .findById(request.getRoom().getId())
                .orElse(null);

        if (room == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Room not found.")
            );
        }

        Guest guest = guestRepository
                .findById(request.getGuest().getId())
                .orElse(null);

        if (guest == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Guest not found.")
            );
        }

        // Check CONFIRMED and CHECKED_IN bookings.
        List<Booking> overlappingBookings =
                bookingRepository
                        .findByRoom_IdAndStatusInAndCheckInLessThanAndCheckOutGreaterThan(
                                room.getId(),
                                List.of("CONFIRMED", "CHECKED_IN"),
                                request.getCheckOut(),
                                request.getCheckIn()
                        );

        if (!overlappingBookings.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Room is already booked for these dates."
                    ));
        }

        // Maintenance end date is inclusive.
        // Checkout date is exclusive.
        List<Maintenance> maintenance =
                maintenanceRepository
                        .findByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThanEqual(
                                room.getId(),
                                List.of("SCHEDULED", "IN_PROGRESS"),
                                request.getCheckOut(),
                                request.getCheckIn()
                        );

        if (!maintenance.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Room is unavailable due to maintenance."
                    ));
        }

        long nights = ChronoUnit.DAYS.between(
                request.getCheckIn(),
                request.getCheckOut()
        );

        BigDecimal totalCost = room.getRatePerNight()
                .multiply(BigDecimal.valueOf(nights));

        Booking booking = new Booking();

        booking.setRoom(room);
        booking.setGuest(guest);
        booking.setCheckIn(request.getCheckIn());
        booking.setCheckOut(request.getCheckOut());
        booking.setTotalCost(totalCost);
        booking.setStatus("CONFIRMED");

        Booking saved = bookingRepository.save(booking);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingById(
            @PathVariable Long id) {

        return bookingRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Booking not found."
                        )));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id) {

        Booking booking = bookingRepository
                .findById(id)
                .orElse(null);

        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Booking not found."
                    ));
        }

        if (!"CONFIRMED".equals(booking.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Only confirmed bookings can be cancelled."
                    ));
        }

        booking.setStatus("CANCELLED");

        return ResponseEntity.ok(
                bookingRepository.save(booking)
        );
    }
}