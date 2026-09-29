
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Booking;
import com.bookmyroom.bookmyroom.model.Payment;
import com.bookmyroom.bookmyroom.repository.BookingRepository;
import com.bookmyroom.bookmyroom.repository.PaymentRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    private static final Set<String> METHODS =
            Set.of("CASH", "UPI", "CARD", "BANK_TRANSFER");

    private static final Set<String> STATUSES =
            Set.of("PENDING", "SUCCESS", "FAILED");

    public PaymentController(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository) {

        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    // 1. RECORD PAYMENT
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Payment createPayment(
            @Valid @RequestBody Payment payment) {

        if (payment.getBooking() == null
                || payment.getBooking().getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking ID is required");
        }

        Booking booking = bookingRepository
                .findById(payment.getBooking().getId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Booking not found"));

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot record payment for cancelled booking");
        }

        if (!METHODS.contains(payment.getPaymentMethod())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment method");
        }

        String reference = payment.getTransactionReference();

        if (reference != null && !reference.isBlank()
                && paymentRepository
                .existsByTransactionReference(reference)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction reference already exists");
        }

        BigDecimal paid = getSuccessfulTotal(booking.getId());

        BigDecimal pending = getPendingTotal(booking.getId());

        BigDecimal remaining = booking.getTotalCost()
                .subtract(paid)
                .subtract(pending);

        if (payment.getAmount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment exceeds outstanding balance");
        }

        payment.setId(null);
        payment.setBooking(booking);
        payment.setStatus("PENDING");
        payment.setPaymentDate(null);

        return paymentRepository.save(payment);
    }

    // 2. VIEW ALL PAYMENTS
    @GetMapping
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // 3. VIEW PAYMENT BY ID
    @GetMapping("/{id}")
    public Payment getPaymentById(@PathVariable Long id) {

        return paymentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Payment not found"));
    }

    // 4. VIEW PAYMENTS FOR A BOOKING
    @GetMapping("/booking/{bookingId}")
    public List<Payment> getBookingPayments(
            @PathVariable Long bookingId) {

        findBooking(bookingId);

        return paymentRepository.findByBooking_Id(bookingId);
    }

    // 5. BOOKING PAYMENT SUMMARY
    @GetMapping("/booking/{bookingId}/summary")
    public Map<String, Object> getPaymentSummary(
            @PathVariable Long bookingId) {

        Booking booking = findBooking(bookingId);

        BigDecimal paid = getSuccessfulTotal(bookingId);

        BigDecimal pending = getPendingTotal(bookingId);

        BigDecimal balance =
                booking.getTotalCost().subtract(paid);

        return Map.of(
                "bookingId", bookingId,
                "totalCost", booking.getTotalCost(),
                "paidAmount", paid,
                "pendingAmount", pending,
                "outstandingBalance", balance
        );
    }

    // 6. UPDATE PAYMENT STATUS
    @PutMapping("/{id}/status")
    @Transactional
    public Payment updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        Payment payment = getPaymentById(id);

        if (!STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment status");
        }

        if (!"PENDING".equals(payment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending payments can be updated");
        }

        if ("CANCELLED".equals(
                payment.getBooking().getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking is cancelled");
        }

        payment.setStatus(status);

        return paymentRepository.save(payment);
    }

    private Booking findBooking(Long bookingId) {

        return bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Booking not found"));
    }

    private BigDecimal getSuccessfulTotal(Long bookingId) {

        return paymentRepository
                .findByBooking_IdAndStatus(
                        bookingId, "SUCCESS")
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPendingTotal(Long bookingId) {

        return paymentRepository
                .findByBooking_IdAndStatus(
                        bookingId, "PENDING")
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}