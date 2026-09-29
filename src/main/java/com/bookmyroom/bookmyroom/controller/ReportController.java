
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.*;
import com.bookmyroom.bookmyroom.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRepository maintenanceRepository;

    public ReportController(
            RoomRepository roomRepository,
            GuestRepository guestRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            MaintenanceRepository maintenanceRepository) {

        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    // 1. OVERALL DASHBOARD
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {

        List<Booking> bookings = bookingRepository.findAll();

        long confirmedBookings = bookings.stream()
                .filter(b -> "CONFIRMED".equals(b.getStatus()))
                .count();

        long checkedInGuests = bookings.stream()
                .filter(b -> "CHECKED_IN".equals(b.getStatus()))
                .count();

        long cancelledBookings = bookings.stream()
                .filter(b -> "CANCELLED".equals(b.getStatus()))
                .count();

        BigDecimal totalRevenue = paymentRepository.findAll()
                .stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("totalRooms", roomRepository.count());
        dashboard.put("totalGuests", guestRepository.count());
        dashboard.put("totalBookings", bookings.size());
        dashboard.put("confirmedBookings", confirmedBookings);
        dashboard.put("checkedInGuests", checkedInGuests);
        dashboard.put("cancelledBookings", cancelledBookings);
        dashboard.put("totalRevenue", totalRevenue);

        return dashboard;
    }

    // 2. MONTHLY BOOKING REPORT
    @GetMapping("/bookings/monthly")
    public Map<String, Object> getMonthlyBookings(
            @RequestParam int year,
            @RequestParam int month) {

        YearMonth period = validateMonth(year, month);

        List<Booking> bookings = bookingRepository.findAll()
                .stream()
                .filter(b -> !"CANCELLED".equals(b.getStatus()))
                .filter(b ->
                        YearMonth.from(b.getCheckIn()).equals(period))
                .toList();

        BigDecimal bookingValue = bookings.stream()
                .map(Booking::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "year", year,
                "month", month,
                "totalBookings", bookings.size(),
                "totalBookingValue", bookingValue,
                "bookings", bookings
        );
    }

    // 3. MONTHLY REVENUE REPORT
    @GetMapping("/revenue/monthly")
    public Map<String, Object> getMonthlyRevenue(
            @RequestParam int year,
            @RequestParam int month) {

        YearMonth period = validateMonth(year, month);

        List<Payment> payments = paymentRepository.findAll()
                .stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .filter(p -> p.getPaymentDate() != null)
                .filter(p ->
                        YearMonth.from(p.getPaymentDate())
                                .equals(period))
                .toList();

        BigDecimal revenue = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "year", year,
                "month", month,
                "successfulPayments", payments.size(),
                "totalRevenue", revenue,
                "payments", payments
        );
    }

    // 4. ROOM OCCUPANCY REPORT
    @GetMapping("/occupancy")
    public Map<String, Object> getOccupancy(
            @RequestParam LocalDate date) {

        List<Room> rooms = roomRepository.findAll();

        Set<Long> occupiedRoomIds = bookingRepository.findAll()
                .stream()
                .filter(b ->
                        "CONFIRMED".equals(b.getStatus())
                                || "CHECKED_IN".equals(b.getStatus()))
                .filter(b ->
                        !date.isBefore(b.getCheckIn())
                                && date.isBefore(b.getCheckOut()))
                .map(b -> b.getRoom().getId())
                .collect(Collectors.toSet());

        Set<Long> maintenanceRoomIds =
                maintenanceRepository.findAll()
                        .stream()
                        .filter(m ->
                                "SCHEDULED".equals(m.getStatus())
                                        || "IN_PROGRESS".equals(m.getStatus()))
                        .filter(m ->
                                !date.isBefore(m.getStartDate())
                                        && !date.isAfter(m.getEndDate()))
                        .map(m -> m.getRoom().getId())
                        .collect(Collectors.toSet());

        List<Room> availableRooms = rooms.stream()
                .filter(r -> !occupiedRoomIds.contains(r.getId()))
                .filter(r -> !maintenanceRoomIds.contains(r.getId()))
                .toList();

        double occupancyPercentage = rooms.isEmpty()
                ? 0.0
                : occupiedRoomIds.size() * 100.0 / rooms.size();

        Map<String, Object> report = new LinkedHashMap<>();

        report.put("date", date);
        report.put("totalRooms", rooms.size());
        report.put("occupiedRooms", occupiedRoomIds.size());
        report.put("roomsUnderMaintenance",
                maintenanceRoomIds.size());
        report.put("availableRooms", availableRooms.size());
        report.put("occupancyPercentage", occupancyPercentage);
        report.put("availableRoomDetails", availableRooms);

        return report;
    }

    // 5. ROOM BOOKING STATISTICS
    @GetMapping("/rooms/popular")
    public List<Map<String, Object>> getRoomStatistics() {

        List<Booking> bookings = bookingRepository.findAll();

        return roomRepository.findAll()
                .stream()
                .map(room -> {

                    long bookingCount = bookings.stream()
                            .filter(b ->
                                    b.getRoom().getId()
                                            .equals(room.getId()))
                            .filter(b ->
                                    !"CANCELLED".equals(b.getStatus()))
                            .count();

                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("roomId", room.getId());
                    result.put("roomNumber",
                            room.getRoomNumber());
                    result.put("roomType",
                            room.getRoomType());
                    result.put("bookingCount", bookingCount);

                    return result;
                })
                .sorted((a, b) -> Long.compare(
                        (Long) b.get("bookingCount"),
                        (Long) a.get("bookingCount")))
                .toList();
    }

    // 6. PAYMENT STATUS SUMMARY
    @GetMapping("/payments/status")
    public Map<String, Object> getPaymentStatusReport() {

        List<Payment> payments = paymentRepository.findAll();

        Map<String, Long> statusCounts = payments.stream()
                .collect(Collectors.groupingBy(
                        Payment::getStatus,
                        Collectors.counting()
                ));

        BigDecimal successfulAmount = payments.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = payments.stream()
                .filter(p -> "PENDING".equals(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalPayments", payments.size(),
                "statusCounts", statusCounts,
                "successfulAmount", successfulAmount,
                "pendingAmount", pendingAmount
        );
    }

    private YearMonth validateMonth(int year, int month) {

        if (year < 2000 || year > 2100
                || month < 1 || month > 12) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid year and month");
        }

        return YearMonth.of(year, month);
    }
}