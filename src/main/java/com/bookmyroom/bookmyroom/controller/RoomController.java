
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Room;
import com.bookmyroom.bookmyroom.repository.RoomRepository;
import com.bookmyroom.bookmyroom.repository.BookingRepository;
import com.bookmyroom.bookmyroom.repository.MaintenanceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final MaintenanceRepository maintenanceRepository;

    public RoomController(
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            MaintenanceRepository maintenanceRepository) {

        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    // 1. CREATE ROOM

    @PostMapping
    public ResponseEntity<?> createRoom(
            @RequestBody Room room) {

        if (room.getRoomNumber() == null
                || room.getRoomNumber().isBlank()
                || room.getRoomType() == null
                || room.getRoomType().isBlank()
                || room.getCapacity() <= 0
                || room.getRatePerNight() == null
                || room.getRatePerNight().signum() <= 0) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "Valid room number, type, capacity "
                                    + "and rate per night are required."
                    )
            );
        }

        if (roomRepository.existsByRoomNumber(
                room.getRoomNumber())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Room number already exists."
                    ));
        }

        room.setId(null);

        Room savedRoom = roomRepository.save(room);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRoom);
    }

    // 2. GET ALL ROOMS

    @GetMapping
    public List<Room> getAllRooms() {

        return roomRepository.findAll();
    }

    // 3. GET ROOM BY ID

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoomById(
            @PathVariable Long id) {

        return roomRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Room not found."
                        )));
    }

    // 4. GET AVAILABLE ROOMS

    @GetMapping("/available")
    public ResponseEntity<?> getAvailableRooms(
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut) {

        if (!checkIn.isBefore(checkOut)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "Checkout must be after check-in."
                    ));
        }

        List<Room> availableRooms =
                roomRepository.findAll()
                        .stream()
                        .filter(room -> {

                            // Check confirmed and checked-in bookings.

                            boolean hasBooking =
                                    !bookingRepository
                                            .findByRoom_IdAndStatusInAndCheckInLessThanAndCheckOutGreaterThan(
                                                    room.getId(),
                                                    List.of(
                                                            "CONFIRMED",
                                                            "CHECKED_IN"
                                                    ),
                                                    checkOut,
                                                    checkIn
                                            )
                                            .isEmpty();

                            // Check scheduled and ongoing maintenance.
                            // Maintenance end date is inclusive.

                            boolean hasMaintenance =
                                    !maintenanceRepository
                                            .findByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThanEqual(
                                                    room.getId(),
                                                    List.of(
                                                            "SCHEDULED",
                                                            "IN_PROGRESS"
                                                    ),
                                                    checkOut,
                                                    checkIn
                                            )
                                            .isEmpty();

                            return !hasBooking && !hasMaintenance;
                        })
                        .toList();

        return ResponseEntity.ok(availableRooms);
    }

    // 5. UPDATE ROOM

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long id,
            @RequestBody Room request) {

        Room room = roomRepository.findById(id)
                .orElse(null);

        if (room == null) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Room not found."
                    ));
        }

        if (request.getRoomNumber() == null
                || request.getRoomNumber().isBlank()
                || request.getRoomType() == null
                || request.getRoomType().isBlank()
                || request.getCapacity() <= 0
                || request.getRatePerNight() == null
                || request.getRatePerNight().signum() <= 0) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "Valid room details are required."
                    ));
        }

        if (!room.getRoomNumber()
                .equals(request.getRoomNumber())
                && roomRepository.existsByRoomNumber(
                request.getRoomNumber())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Room number already exists."
                    ));
        }

        room.setRoomNumber(request.getRoomNumber());
        room.setRoomType(request.getRoomType());
        room.setCapacity(request.getCapacity());
        room.setRatePerNight(request.getRatePerNight());

        Room updatedRoom = roomRepository.save(room);

        return ResponseEntity.ok(updatedRoom);
    }

    // 6. DELETE ROOM

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable Long id) {

        if (!roomRepository.existsById(id)) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Room not found."
                    ));
        }

        if (bookingRepository.existsByRoom_Id(id)) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Cannot delete a room with booking history."
                    ));
        }

        if (!maintenanceRepository
                .findByRoom_Id(id)
                .isEmpty()) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Cannot delete a room with maintenance history."
                    ));
        }

        roomRepository.deleteById(id);

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Room deleted successfully."
                )
        );
    }
}