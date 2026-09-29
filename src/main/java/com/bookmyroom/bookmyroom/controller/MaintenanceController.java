
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Booking;
import com.bookmyroom.bookmyroom.model.Maintenance;
import com.bookmyroom.bookmyroom.model.Room;

import com.bookmyroom.bookmyroom.repository.BookingRepository;
import com.bookmyroom.bookmyroom.repository.MaintenanceRepository;
import com.bookmyroom.bookmyroom.repository.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceRepository maintenanceRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public MaintenanceController(
            MaintenanceRepository maintenanceRepository,
            RoomRepository roomRepository,
            BookingRepository bookingRepository) {

        this.maintenanceRepository = maintenanceRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // CREATE MAINTENANCE

    @PostMapping
    public ResponseEntity<?> createMaintenance(
            @RequestBody Maintenance request) {

        if (request.getRoom() == null
                || request.getRoom().getId() == null
                || request.getReason() == null
                || request.getReason().isBlank()
                || request.getStartDate() == null
                || request.getEndDate() == null) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Room, reason and dates are required.")
            );
        }

        if (request.getEndDate()
                .isBefore(request.getStartDate())) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "End date cannot be before start date.")
            );
        }

        Room room = roomRepository
                .findById(request.getRoom().getId())
                .orElse(null);

        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Room not found."));
        }

        if (hasBookingConflict(
                room.getId(),
                request.getStartDate(),
                request.getEndDate())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Maintenance conflicts with an existing booking."));
        }

        if (hasMaintenanceConflict(
                room.getId(),
                request.getStartDate(),
                request.getEndDate())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Maintenance is already scheduled for these dates."));
        }

        Maintenance maintenance = new Maintenance();

        maintenance.setRoom(room);
        maintenance.setReason(request.getReason());
        maintenance.setStartDate(request.getStartDate());
        maintenance.setEndDate(request.getEndDate());
        maintenance.setStatus("SCHEDULED");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(maintenanceRepository.save(maintenance));
    }

    // GET ALL MAINTENANCE RECORDS

    @GetMapping
    public List<Maintenance> getAllMaintenance() {
        return maintenanceRepository.findAll();
    }

    // GET MAINTENANCE BY ID

    @GetMapping("/{id}")
    public ResponseEntity<?> getMaintenanceById(
            @PathVariable Long id) {

        return maintenanceRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Maintenance record not found."
                        )));
    }

    // GET MAINTENANCE BY ROOM

    @GetMapping("/room/{roomId}")
    public List<Maintenance> getMaintenanceByRoom(
            @PathVariable Long roomId) {

        return maintenanceRepository.findByRoom_Id(roomId);
    }

    // UPDATE SCHEDULED MAINTENANCE

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMaintenance(
            @PathVariable Long id,
            @RequestBody Maintenance request) {

        Maintenance maintenance = maintenanceRepository
                .findById(id)
                .orElse(null);

        if (maintenance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Maintenance record not found."
                    ));
        }

        if (!"SCHEDULED".equals(maintenance.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Only scheduled maintenance can be updated."
                    ));
        }

        if (request.getReason() == null
                || request.getReason().isBlank()
                || request.getStartDate() == null
                || request.getEndDate() == null
                || request.getEndDate()
                .isBefore(request.getStartDate())) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Valid reason and dates are required."
                    ));
        }

        Long roomId = maintenance.getRoom().getId();

        if (hasBookingConflict(
                roomId,
                request.getStartDate(),
                request.getEndDate())) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Updated dates conflict with a booking."
                    ));
        }

        List<Maintenance> conflicts =
                maintenanceRepository
                        .findByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThanEqual(
                                roomId,
                                List.of("SCHEDULED", "IN_PROGRESS"),
                                request.getEndDate().plusDays(1),
                                request.getStartDate()
                        );

        boolean hasOtherMaintenance = conflicts.stream()
                .anyMatch(item ->
                        !item.getId().equals(maintenance.getId()));

        if (hasOtherMaintenance) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Updated dates conflict with other maintenance."
                    ));
        }

        maintenance.setReason(request.getReason());
        maintenance.setStartDate(request.getStartDate());
        maintenance.setEndDate(request.getEndDate());

        return ResponseEntity.ok(
                maintenanceRepository.save(maintenance)
        );
    }

    // START MAINTENANCE

    @PutMapping("/{id}/start")
    public ResponseEntity<?> startMaintenance(
            @PathVariable Long id) {

        Maintenance maintenance = maintenanceRepository
                .findById(id)
                .orElse(null);

        if (maintenance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Maintenance record not found."
                    ));
        }

        if (!"SCHEDULED".equals(maintenance.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Only scheduled maintenance can be started."
                    ));
        }

        maintenance.setStatus("IN_PROGRESS");

        return ResponseEntity.ok(
                maintenanceRepository.save(maintenance)
        );
    }

    // COMPLETE MAINTENANCE

    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeMaintenance(
            @PathVariable Long id) {

        Maintenance maintenance = maintenanceRepository
                .findById(id)
                .orElse(null);

        if (maintenance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Maintenance record not found."
                    ));
        }

        if (!"IN_PROGRESS".equals(maintenance.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Only maintenance in progress can be completed."
                    ));
        }

        maintenance.setStatus("COMPLETED");

        return ResponseEntity.ok(
                maintenanceRepository.save(maintenance)
        );
    }

    // DELETE SCHEDULED MAINTENANCE

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMaintenance(
            @PathVariable Long id) {

        Maintenance maintenance = maintenanceRepository
                .findById(id)
                .orElse(null);

        if (maintenance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Maintenance record not found."
                    ));
        }

        if (!"SCHEDULED".equals(maintenance.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Only scheduled maintenance can be deleted."
                    ));
        }

        maintenanceRepository.delete(maintenance);

        return ResponseEntity.ok(
                Map.of("message",
                        "Maintenance deleted successfully.")
        );
    }

    // CHECK BOOKING CONFLICTS

    private boolean hasBookingConflict(
            Long roomId,
            LocalDate startDate,
            LocalDate endDate) {

        List<Booking> conflicts =
                bookingRepository
                        .findByRoom_IdAndStatusInAndCheckInLessThanAndCheckOutGreaterThan(
                                roomId,
                                List.of("CONFIRMED", "CHECKED_IN"),
                                endDate.plusDays(1),
                                startDate
                        );

        return !conflicts.isEmpty();
    }

    // CHECK MAINTENANCE CONFLICTS

    private boolean hasMaintenanceConflict(
            Long roomId,
            LocalDate startDate,
            LocalDate endDate) {

        return !maintenanceRepository
                .findByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThanEqual(
                        roomId,
                        List.of("SCHEDULED", "IN_PROGRESS"),
                        endDate.plusDays(1),
                        startDate
                )
                .isEmpty();
    }
}